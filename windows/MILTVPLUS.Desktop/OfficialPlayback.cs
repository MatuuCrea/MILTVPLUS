using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;

namespace MILTVPLUS.Desktop;

public partial class MainWindow
{
    private readonly List<CatalogEntry> _watchQueue = [];
    private int _watchQueueIndex = -1;
    private bool _officialWebViewReady;
    private DispatcherTimer? _watchQueueRefreshTimer;

    private async void Watch_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not CatalogEntry selected) return;
        if (Sections.SelectedItem is ListBoxItem section && section.Tag?.ToString() == "Música")
        {
            PlayEntry(selected);
            return;
        }
        var visible = CatalogScroll.ItemsSource as IEnumerable<CatalogEntry>;
        _watchQueue.Clear();
        _watchQueue.AddRange((visible ?? entries).Where(IsWatchable));
        if (!_watchQueue.Any(x => ReferenceEquals(x, selected) || x.Title == selected.Title))
            _watchQueue.Add(selected);
        _watchQueueIndex = _watchQueue.FindIndex(x => ReferenceEquals(x, selected) || x.Title == selected.Title);
        StartWatchQueueRefresh();
        await WatchEntryAsync(selected);
    }

    private bool IsWatchable(CatalogEntry entry) =>
        entry.IsUserProvidedSource || (IsHttps(entry.StreamUrl) && entry.HasActivePlaybackRights) || IsHttps(entry.OfficialWatchUrl);

    private static bool IsHttps(string? value) =>
        Uri.TryCreate(value, UriKind.Absolute, out var uri) &&
        uri.Scheme.Equals(Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase) &&
        !string.IsNullOrWhiteSpace(uri.Host) &&
        string.IsNullOrEmpty(uri.UserInfo);

    private void StartWatchQueueRefresh()
    {
        if (_watchQueueRefreshTimer is null)
        {
            _watchQueueRefreshTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(65) };
            _watchQueueRefreshTimer.Tick += (_, _) => SyncWatchQueueWithCatalog();
        }
        _watchQueueRefreshTimer.Start();
    }

    private void SyncWatchQueueWithCatalog()
    {
        if (CatalogScroll.ItemsSource is not IEnumerable<CatalogEntry> visible) return;
        var refreshed = visible.Where(IsWatchable).ToList();
        if (refreshed.Count == 0) return;
        var currentTitle = _currentEntry?.Title;
        var updatedCurrent = currentTitle is null
            ? null
            : refreshed.FirstOrDefault(x => string.Equals(x.Title, currentTitle, StringComparison.OrdinalIgnoreCase));
        _watchQueue.Clear();
        _watchQueue.AddRange(refreshed);
        _watchQueueIndex = updatedCurrent is null ? -1 : _watchQueue.IndexOf(updatedCurrent);
        if (updatedCurrent is not null && _currentEntry is not null &&
            (!string.Equals(updatedCurrent.StreamUrl, _currentEntry.StreamUrl, StringComparison.Ordinal) ||
             !string.Equals(updatedCurrent.OfficialWatchUrl, _currentEntry.OfficialWatchUrl, StringComparison.Ordinal)))
        {
            _ = WatchEntryAsync(updatedCurrent);
        }
    }

    private async Task EnsureOfficialWebViewAsync()
    {
        if (_officialWebViewReady) return;
        await OfficialWebView.EnsureCoreWebView2Async();
        var core = OfficialWebView.CoreWebView2;
        core.Settings.AreDefaultContextMenusEnabled = false;
        core.Settings.AreDevToolsEnabled = false;
        core.Settings.IsStatusBarEnabled = false;
        core.Settings.IsZoomControlEnabled = false;
        core.Settings.IsScriptEnabled = true;
        core.NewWindowRequested += (_, args) =>
        {
            args.Handled = true;
            if (IsHttps(args.Uri)) core.Navigate(args.Uri);
        };
        core.NavigationStarting += (_, args) =>
        {
            if (!IsHttps(args.Uri))
            {
                args.Cancel = true;
                StatusText.Text = "Se bloqueó una navegación que no usa HTTPS.";
            }
        };
        core.NavigationCompleted += (_, args) =>
        {
            if (args.IsSuccess)
                StatusText.Text = "Canal oficial abierto en el reproductor integrado.";
            else
                StatusText.Text = "El emisor no permitió cargar el reproductor integrado. MILTVPLUS permanece abierta; esta señal no está disponible en el reproductor integrado desde este dispositivo o región.";
        };
        _officialWebViewReady = true;
    }

    private async Task WatchEntryAsync(CatalogEntry entry)
    {
        var isYouTubeLive = string.Equals(entry.PlaybackType, "youtube_live", StringComparison.OrdinalIgnoreCase);
        var officialUrl = isYouTubeLive ? entry.StreamUrl : entry.OfficialWatchUrl;
        if (!isYouTubeLive && IsHttps(entry.StreamUrl) && entry.HasActivePlaybackRights)
        {
            OfficialWebView.Visibility = Visibility.Collapsed;
            VideoSurface.Visibility = Visibility.Visible;
            PlayEntry(entry);
            if (!_isFullscreen) ToggleFullscreen();
            return;
        }

        if (!IsHttps(officialUrl))
        {
            StatusText.Text = "Esta ficha no tiene una señal directa ni una página HTTPS oficial.";
            return;
        }

        try
        {
            _currentEntry = entry;
            _mediaPlayer.Stop();
            _currentMedia?.Dispose();
            _currentMedia = null;
            VideoSurface.Visibility = Visibility.Collapsed;
            OfficialWebView.Visibility = Visibility.Visible;
            StatusText.Text = "Preparando el reproductor oficial de " + entry.Title + "…";
            if (!_isFullscreen) ToggleFullscreen();
            await EnsureOfficialWebViewAsync();
            OfficialWebView.CoreWebView2.Navigate(officialUrl!);
        }
        catch (Exception ex)
        {
            // Never eject the user from MILTVPLUS to an external browser.
            OfficialWebView.Visibility = Visibility.Visible;
            VideoSurface.Visibility = Visibility.Collapsed;
            StatusText.Text = "El reproductor integrado no pudo iniciar esta señal: " + ex.Message +
                ". MILTVPLUS permanece abierta; puede tratarse de una restricción del emisor, DRM o WebView2.";
        }
    }

    private async void PreviousAnyChannel_Click(object sender, RoutedEventArgs e) => await ChangeWatchChannelAsync(-1);
    private async void NextAnyChannel_Click(object sender, RoutedEventArgs e) => await ChangeWatchChannelAsync(1);

    private async Task ChangeWatchChannelAsync(int delta)
    {
        if (_watchQueue.Count == 0)
        {
            var visible = CatalogScroll.ItemsSource as IEnumerable<CatalogEntry>;
            _watchQueue.AddRange((visible ?? entries).Where(IsWatchable));
        }
        if (_watchQueue.Count == 0)
        {
            StatusText.Text = "No hay canales reproducibles en el filtro actual.";
            return;
        }
        var currentIndex = _watchQueueIndex;
        if (currentIndex < 0 && _currentEntry is not null)
            currentIndex = _watchQueue.FindIndex(x => ReferenceEquals(x, _currentEntry) || x.Title == _currentEntry.Title);
        _watchQueueIndex = currentIndex < 0
            ? (delta > 0 ? 0 : _watchQueue.Count - 1)
            : (currentIndex + delta + _watchQueue.Count) % _watchQueue.Count;
        await WatchEntryAsync(_watchQueue[_watchQueueIndex]);
    }

    private void StopAny_Click(object sender, RoutedEventArgs e)
    {
        _watchQueueRefreshTimer?.Stop();
        Stop_Click(sender, e);
        if (_officialWebViewReady) OfficialWebView.CoreWebView2.Stop();
        OfficialWebView.Visibility = Visibility.Collapsed;
        VideoSurface.Visibility = Visibility.Visible;
        _watchQueue.Clear();
        _watchQueueIndex = -1;
        if (_isFullscreen) ToggleFullscreen();
    }
}
