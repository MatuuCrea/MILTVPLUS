using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using Microsoft.Web.WebView2.Core;

namespace MILTVPLUS.Desktop;

public partial class MainWindow
{
    private readonly List<CatalogEntry> _watchQueue = [];
    private int _watchQueueIndex = -1;
    private bool _officialWebViewReady;

    private async void Watch_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not CatalogEntry selected) return;
        var visible = Catalog.ItemsSource as IEnumerable<CatalogEntry>;
        _watchQueue.Clear();
        _watchQueue.AddRange((visible ?? entries).Where(IsWatchable));
        if (!_watchQueue.Any(x => ReferenceEquals(x, selected) || x.Title == selected.Title))
            _watchQueue.Add(selected);
        _watchQueueIndex = _watchQueue.FindIndex(x => ReferenceEquals(x, selected) || x.Title == selected.Title);
        await WatchEntryAsync(selected);
    }

    private bool IsWatchable(CatalogEntry entry) =>
        IsHttps(entry.StreamUrl) || IsHttps(entry.OfficialWatchUrl);

    private static bool IsHttps(string? value) =>
        Uri.TryCreate(value, UriKind.Absolute, out var uri) &&
        uri.Scheme.Equals(Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase) &&
        !string.IsNullOrWhiteSpace(uri.Host) &&
        string.IsNullOrEmpty(uri.UserInfo);

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
                StatusText.Text = "El emisor no permitió cargar el reproductor integrado. Podés abrir la página en el navegador.";
        };
        _officialWebViewReady = true;
    }

    private async Task WatchEntryAsync(CatalogEntry entry)
    {
        if (IsHttps(entry.StreamUrl))
        {
            OfficialWebView.Visibility = Visibility.Collapsed;
            VideoSurface.Visibility = Visibility.Visible;
            PlayEntry(entry);
            if (!_isFullscreen) ToggleFullscreen();
            return;
        }

        if (!IsHttps(entry.OfficialWatchUrl))
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
            await EnsureOfficialWebViewAsync();
            OfficialWebView.CoreWebView2.Navigate(entry.OfficialWatchUrl!);
            if (!_isFullscreen) ToggleFullscreen();
        }
        catch (Exception ex)
        {
            OfficialWebView.Visibility = Visibility.Collapsed;
            VideoSurface.Visibility = Visibility.Visible;
            StatusText.Text = "No se pudo iniciar el visor integrado: " + ex.Message;
            try
            {
                Process.Start(new ProcessStartInfo(entry.OfficialWatchUrl!) { UseShellExecute = true });
                StatusText.Text += " Se abrió la página oficial en el navegador externo.";
            }
            catch (Exception browserError)
            {
                StatusText.Text += " Tampoco se pudo abrir el navegador: " + browserError.Message;
            }
        }
    }

    private async void PreviousAnyChannel_Click(object sender, RoutedEventArgs e) => await ChangeWatchChannelAsync(-1);
    private async void NextAnyChannel_Click(object sender, RoutedEventArgs e) => await ChangeWatchChannelAsync(1);

    private async Task ChangeWatchChannelAsync(int delta)
    {
        if (_watchQueue.Count == 0)
        {
            var visible = Catalog.ItemsSource as IEnumerable<CatalogEntry>;
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
        Stop_Click(sender, e);
        if (_officialWebViewReady) OfficialWebView.CoreWebView2.Stop();
        OfficialWebView.Visibility = Visibility.Collapsed;
        VideoSurface.Visibility = Visibility.Visible;
        _watchQueue.Clear();
        _watchQueueIndex = -1;
        if (_isFullscreen) ToggleFullscreen();
    }
}
