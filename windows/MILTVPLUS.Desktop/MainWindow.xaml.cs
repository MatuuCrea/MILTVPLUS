using System.Net.Http;
using System.Reflection;
using System.IO;
using System.Diagnostics;
using System.Text.Json;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Serialization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Threading;
using Microsoft.Win32;
using LibVLCSharp.Shared;

namespace MILTVPLUS.Desktop;

public partial class MainWindow : Window
{
    private const string Api = "https://rbnuxapfsnaabrzuxvdy.supabase.co/rest/v1/";
    private const string PublicKey = "sb_publishable_tmBU-IB7P0db0w7RG9TMEw_GD754hb7";
    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(20) };
    private static readonly HttpClient ResolverClient = new() { Timeout = TimeSpan.FromSeconds(90) };
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private readonly LibVLC _libVlc;
    private readonly MediaPlayer _mediaPlayer;
    private Media? _currentMedia;
    private List<CatalogEntry> entries = [];
    private string table = "tv_channels";
    private string select = "id,title,description,country_code,created_at,category,language_code,access_model,logo_url,official_watch_url,stream_url,playback_type,is_free_to_air,source_status,last_checked_at,last_check_detail";
    private string order = "created_at.desc";
    private string? kind;
    private UpdateRelease? _availableUpdate;
    private bool _updatingFilters;
    private bool _isFullscreen;
    private bool _localPlaylistLoaded;
    private CatalogEntry? _currentEntry;
    private readonly DispatcherTimer _catalogRefreshTimer = new() { Interval = TimeSpan.FromMinutes(1) };
    private readonly DispatcherTimer _clockTimer = new() { Interval = TimeSpan.FromSeconds(1) };
    private bool _adultUnlocked;
    private string? _contentCategoryFilter;
    private DesktopSettings _desktopSettings = new();
    private string AdultDataDirectory => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MILTVPLUS");
    private string DesktopSettingsPath => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MILTVPLUS", "desktop-settings.json");

    public MainWindow()
    {
        Core.Initialize();
        _libVlc = new LibVLC("--no-video-title-show", "--network-caching=2000", "--live-caching=1500", "--http-reconnect", "--avcodec-hw=any");
        _mediaPlayer = new MediaPlayer(_libVlc);
        InitializeComponent();
        LoadDesktopSettings();
        VideoSurface.MediaPlayer = _mediaPlayer;
        _mediaPlayer.EndReached += (_, _) =>
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                if (Sections.SelectedItem is ListBoxItem selected && selected.Tag?.ToString() == "Música")
                    ChangeChannel(1);
            }));
        };
        UpdateClock();
        _clockTimer.Tick += (_, _) => UpdateClock();
        _clockTimer.Start();
        AdultAgeConfirm.IsChecked = File.Exists(Path.Combine(AdultDataDirectory, "adult-age-confirmed"));
        Client.DefaultRequestHeaders.TryAddWithoutValidation("apikey", PublicKey);
        Client.DefaultRequestHeaders.TryAddWithoutValidation("Accept", "application/json");
        Loaded += async (_, _) => { Window_SizeChanged(this, null!); await LoadCatalog(); if (_desktopSettings.AutoCheckUpdates) await CheckForUpdates(silent: true); if (_desktopSettings.StartMaximized) WindowState = WindowState.Maximized; };
        Closed += (_, _) => { _catalogRefreshTimer.Stop(); _clockTimer.Stop(); DisposePlayer(); };
        _catalogRefreshTimer.Tick += async (_, _) => { if (table == "tv_channels" && !_localPlaylistLoaded) await LoadCatalog(); };
        _catalogRefreshTimer.Start();
        VideoSurface.MouseLeftButtonDown += (_, e) => { if (e.ClickCount == 2) ToggleFullscreen(); };
    }

    private void Window_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        // Keep the desktop layout usable on compact Windows windows without changing its navigation model.
        // WPF ActualWidth is measured in device-independent pixels; validate real DPI scaling before release.
        var compact = ActualWidth < 1080;
        SidebarColumn.Width = new GridLength(compact ? 184 : 232);
        MainContentGrid.Margin = new Thickness(compact ? 12 : 22);
        SearchBox.Width = compact ? 120 : 220;
        ToolbarScroll.MaxWidth = compact ? 280 : 720;
    }

    private void LoadDesktopSettings()
    {
        try
        {
            if (File.Exists(DesktopSettingsPath))
                _desktopSettings = JsonSerializer.Deserialize<DesktopSettings>(File.ReadAllText(DesktopSettingsPath), JsonOptions) ?? new DesktopSettings();
        }
        catch { _desktopSettings = new DesktopSettings(); }
        AutoCheckUpdatesCheckBox.IsChecked = _desktopSettings.AutoCheckUpdates;
        AlwaysOnTopCheckBox.IsChecked = _desktopSettings.AlwaysOnTop;
        StartMaximizedCheckBox.IsChecked = _desktopSettings.StartMaximized;
        DesktopVolumeSlider.Value = Math.Clamp(_desktopSettings.MusicVolume, 0, 100);
        Topmost = _desktopSettings.AlwaysOnTop;
        _mediaPlayer.Volume = (int)DesktopVolumeSlider.Value;
    }

    private void SaveDesktopSettings_Click(object sender, RoutedEventArgs e)
    {
        _desktopSettings = new DesktopSettings
        {
            AutoCheckUpdates = AutoCheckUpdatesCheckBox.IsChecked == true,
            AlwaysOnTop = AlwaysOnTopCheckBox.IsChecked == true,
            StartMaximized = StartMaximizedCheckBox.IsChecked == true,
            MusicVolume = Math.Clamp((int)DesktopVolumeSlider.Value, 0, 100)
        };
        Directory.CreateDirectory(Path.GetDirectoryName(DesktopSettingsPath)!);
        File.WriteAllText(DesktopSettingsPath, JsonSerializer.Serialize(_desktopSettings, new JsonSerializerOptions { WriteIndented = true }));
        Topmost = _desktopSettings.AlwaysOnTop;
        _mediaPlayer.Volume = _desktopSettings.MusicVolume;
        StatusText.Text = "¡Listo! Tu configuración quedó guardada en esta computadora.";
    }

    private async Task LoadCatalog()
    {
        StatusText.Text = "Consultando publicaciones autorizadas…";
        try
        {
            var loaded = new List<CatalogEntry>();
            const int pageSize = 500;
            const int maxRows = 10000;
            for (var offset = 0; offset < maxRows; offset += pageSize)
            {
                var fields = select.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                    .Concat(new[] { "rights_verified", "rights_holder", "rights_reference", "rights_regions", "rights_valid_from", "rights_valid_until" })
                    .Distinct(StringComparer.OrdinalIgnoreCase);
                var query = $"?select={Uri.EscapeDataString(string.Join(",", fields))}&is_published=eq.true&order={Uri.EscapeDataString(order)}&limit={pageSize}&offset={offset}";
                if (kind is not null) query += "&kind=eq." + Uri.EscapeDataString(kind);
                using var response = await Client.GetAsync(Api + table + query);
                if (!response.IsSuccessStatusCode)
                {
                    var detail = await response.Content.ReadAsStringAsync();
                    throw new HttpRequestException($"Supabase {(int)response.StatusCode}: {detail}");
                }
                await using var stream = await response.Content.ReadAsStreamAsync();
                var page = await JsonSerializer.DeserializeAsync<List<CatalogEntry>>(stream, JsonOptions) ?? [];
                loaded.AddRange(page);
                if (page.Count < pageSize) break;
            }
            if (table == "catalog_items")
            {
                var adultSection = Sections.SelectedItem is ListBoxItem selectedItem && selectedItem.Tag?.ToString() == "Adultos 18+";
                loaded = adultSection && _adultUnlocked
                    ? loaded.Where(x => IsAdultRating(x.AgeRating)).ToList()
                    : loaded.Where(x => !IsAdultRating(x.AgeRating)).ToList();
                if (_contentCategoryFilter == "kids")
                    loaded = loaded.Where(x => (x.Genre ?? "").Contains("infantil", StringComparison.OrdinalIgnoreCase) || (x.Genre ?? "").Contains("kids", StringComparison.OrdinalIgnoreCase)).ToList();
                else if (_contentCategoryFilter == "documentary")
                    loaded = loaded.Where(x => (x.Genre ?? "").Contains("documental", StringComparison.OrdinalIgnoreCase) || (x.Genre ?? "").Contains("documentary", StringComparison.OrdinalIgnoreCase)).ToList();
            }
            entries = loaded;
            UpdateFilterOptions();
            StatusText.Text = entries.Count == 0
                ? "Todavía no hay contenido autorizado publicado en esta sección."
                : $"{entries.Count} publicaciones · Contenido publicado";
        }
        catch (Exception ex)
        {
            StatusText.Text = "No se pudo cargar el catálogo: " + ex.Message;
            entries = [];
            CatalogScroll.ItemsSource = entries;
        }
    }

    private void UpdateFilterOptions()
    {
        _updatingFilters = true;
        var previousCountry = CountryFilter.SelectedItem?.ToString() ?? "Todos";
        var previousCategory = CategoryFilter.SelectedItem?.ToString() ?? "Todas";
        var countries = new[] { "Todos" }.Concat(entries.Select(x => x.CountryCode)
            .Where(x => !string.IsNullOrWhiteSpace(x)).Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(x => x, StringComparer.OrdinalIgnoreCase)!).ToList();
        var categories = new[] { "Todas" }.Concat(entries.Select(x => x.Category)
            .Where(x => !string.IsNullOrWhiteSpace(x)).Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(x => x, StringComparer.OrdinalIgnoreCase)!).ToList();
        CountryFilter.ItemsSource = countries;
        CategoryFilter.ItemsSource = categories;
        CountryFilter.SelectedItem = countries.Contains(previousCountry) ? previousCountry : "Todos";
        CategoryFilter.SelectedItem = categories.Contains(previousCategory) ? previousCategory : "Todas";
        _updatingFilters = false;
        ApplySearch();
    }

    private void ApplySearch()
    {
        var term = SearchBox.Text.Trim();
        var country = CountryFilter.SelectedItem?.ToString();
        var category = CategoryFilter.SelectedItem?.ToString();
        CatalogScroll.ItemsSource = entries.Where(x =>
            (string.IsNullOrEmpty(term) ||
                (x.Title + " " + x.Description + " " + x.Genre + " " + x.Creator + " " + x.CountryCode + " " + x.Category + " " + x.LanguageCode + " " + x.SourceStatus + " " + x.AccessModel)
                .Contains(term, StringComparison.OrdinalIgnoreCase)) &&
            (string.IsNullOrEmpty(country) || country == "Todos" || string.Equals(x.CountryCode, country, StringComparison.OrdinalIgnoreCase)) &&
            (string.IsNullOrEmpty(category) || category == "Todas" || string.Equals(x.Category, category, StringComparison.OrdinalIgnoreCase))
        ).ToList();
    }

    private void Filter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded || _updatingFilters) return;
        var code = CountryFilter.SelectedItem?.ToString() ?? "Todos";
        CountryPhrase.Text = code switch
        {
            "AR" => "Argentina - Siempre en tu corazón 🇦🇷",
            "US" => "Estados Unidos · Un mundo de historias 🇺🇸",
            "ES" => "España · Cultura que nos une 🇪🇸",
            "MX" => "México · Tradición y emoción 🇲🇽",
            "CO" => "Colombia · Pasión que se comparte 🇨🇴",
            "CL" => "Chile · Un país, mil paisajes 🇨🇱",
            "BR" => "Brasil · Ritmo y alegría 🇧🇷",
            _ => "Desde Argentina para el mundo 🇦🇷"
        };
        ApplySearch();
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e) => await LoadCatalog();

    private async void CheckUpdates_Click(object sender, RoutedEventArgs e) => await CheckForUpdates(silent: false);

    private async Task CheckForUpdates(bool silent)
    {
        try
        {
            var current = Assembly.GetExecutingAssembly().GetName().Version ?? new Version(22, 34, 9);
            _availableUpdate = await UpdateService.CheckAsync(current);
            if (_availableUpdate is null)
            {
                if (!silent) MessageBox.Show(this, "MILTVPLUS ya está en la última versión oficial publicada.", "Actualizaciones", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            StatusText.Text = $"Nueva versión {_availableUpdate.Version} disponible.";
            if (silent) return;
            var answer = MessageBox.Show(this,
                $"Hay una nueva versión de MILTVPLUS ({_availableUpdate.Version}).\n\nSe descargará el instalador oficial y se verificará su SHA-256 antes de ejecutarlo. ¿Continuar?",
                "Actualización disponible", MessageBoxButton.YesNo, MessageBoxImage.Information);
            if (answer != MessageBoxResult.Yes) return;

            StatusText.Text = "Descargando y verificando instalador oficial…";
            var installer = await UpdateService.DownloadAndVerifyAsync(_availableUpdate);
            UpdateService.LaunchInstaller(installer);
            Application.Current.Shutdown();
        }
        catch (Exception ex)
        {
            StatusText.Text = "No se pudo actualizar: " + ex.Message;
            if (!silent) MessageBox.Show(this, ex.Message, "Error de actualización", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }
    private void SearchBox_TextChanged(object sender, TextChangedEventArgs e) { if (IsLoaded) ApplySearch(); }

    private void Play_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is CatalogEntry item) PlayEntry(item);
    }

    private async void PlayEntry(CatalogEntry item)
    {
        var isMusic = Sections.SelectedItem is ListBoxItem selected && selected.Tag?.ToString() == "Música";
        string? streamUrl = item.StreamUrl ?? item.AuthorizedStreamUrl;
        if (isMusic)
        {
            try { streamUrl = await ResolveMusicUrlAsync(item); }
            catch (Exception ex) { StatusText.Text = "Música no disponible para reproducción interna: " + ex.Message; return; }
        }
        if (!Uri.TryCreate(streamUrl, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps || (!isMusic && !item.HasActivePlaybackRights && !item.IsUserProvidedSource))
        {
            StatusText.Text = isMusic
                ? "Esta pista no tiene una fuente de audio directa disponible para tu región."
                : "Este contenido todavía no tiene una señal HTTPS verificada. No se abrirán aplicaciones externas.";
            return;
        }
        try
        {
            _currentEntry = item;
            _currentMedia?.Dispose();
            _currentMedia = new Media(_libVlc, uri);
            if (!_mediaPlayer.Play(_currentMedia)) throw new InvalidOperationException("El reproductor no pudo iniciar el flujo.");
            StatusText.Text = "Reproduciendo: " + item.Title;
        }
        catch (Exception ex) { StatusText.Text = "Error de reproducción: " + ex.Message; }
    }

    private async Task<string> ResolveMusicUrlAsync(CatalogEntry item)
    {
        if (string.IsNullOrWhiteSpace(item.Id)) throw new InvalidOperationException("La pista no tiene un identificador válido.");
        var resolver = (Environment.GetEnvironmentVariable("MILTVPLUS_RESOLVER_URL") ?? "https://miltvplus-resolver-api.onrender.com").TrimEnd('/');
        if (!Uri.TryCreate(resolver, UriKind.Absolute, out var baseUri) || baseUri.Scheme != Uri.UriSchemeHttps)
            throw new InvalidOperationException("El servicio de música no está configurado.");
        var country = item.CountryCode;
        if (string.IsNullOrWhiteSpace(country) || !System.Text.RegularExpressions.Regex.IsMatch(country, "^[A-Z]{2}$"))
            country = RegionInfo.CurrentRegion.TwoLetterISORegionName;
        using var response = await ResolverClient.GetAsync($"{resolver}/api/v2/resolve/musica/{country}/{Uri.EscapeDataString(item.Id)}");
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var root = document.RootElement;
        if (!response.IsSuccessStatusCode || !root.TryGetProperty("success", out var success) || !success.GetBoolean() ||
            !root.TryGetProperty("mode", out var mode) || mode.GetString() != "direct_stream" ||
            !root.TryGetProperty("mediaType", out var mediaType) || mediaType.GetString() != "audio" ||
            !root.TryGetProperty("streamUrl", out var stream) || !Uri.TryCreate(stream.GetString(), UriKind.Absolute, out var audioUri) ||
            audioUri.Scheme != Uri.UriSchemeHttps)
        {
            var message = root.TryGetProperty("error", out var error) ? error.GetString() : null;
            throw new InvalidOperationException(message ?? "El servidor no autorizó esta pista.");
        }
        return audioUri!.ToString();
    }

    private List<CatalogEntry> CurrentPlayableEntries()
    {
        if (CatalogScroll.ItemsSource is IEnumerable<CatalogEntry> visible)
        {
            var filtered = visible.Where(x => x.HasActivePlaybackRights || x.IsUserProvidedSource).Where(x => Uri.TryCreate(x.StreamUrl, UriKind.Absolute, out var u) && u.Scheme == Uri.UriSchemeHttps).ToList();
            if (filtered.Count > 0) return filtered;
        }
        return entries.Where(x => x.HasActivePlaybackRights || x.IsUserProvidedSource).Where(x => Uri.TryCreate(x.StreamUrl, UriKind.Absolute, out var u) && u.Scheme == Uri.UriSchemeHttps).ToList();
    }

    private void PreviousChannel_Click(object sender, RoutedEventArgs e) => ChangeChannel(-1);
    private void NextChannel_Click(object sender, RoutedEventArgs e) => ChangeChannel(1);

    private void ChangeChannel(int delta)
    {
        var playable = CurrentPlayableEntries();
        if (playable.Count == 0) { StatusText.Text = "No hay señales integradas disponibles para cambiar de canal."; return; }
        var current = _currentEntry is null ? -1 : playable.FindIndex(x => ReferenceEquals(x, _currentEntry) || x.Title == _currentEntry.Title);
        var next = current < 0 ? 0 : (current + delta + playable.Count) % playable.Count;
        PlayEntry(playable[next]);
    }

    private void ToggleFullscreen_Click(object sender, RoutedEventArgs e) => ToggleFullscreen();

    private void ToggleFullscreen()
    {
        if (!_isFullscreen)
        {
            _isFullscreen = true;
            WindowStyle = WindowStyle.None;
            ResizeMode = ResizeMode.NoResize;
            WindowState = WindowState.Maximized;
            MainContentGrid.Margin = new Thickness(0);
            CatalogScroll.Visibility = Visibility.Collapsed;
            VideoContainer.BorderThickness = new Thickness(0);
            VideoContainer.CornerRadius = new CornerRadius(0);
            VideoContainer.Padding = new Thickness(0);
            VideoContainer.Margin = new Thickness(0);
            Grid.SetRow(VideoContainer, 0);
            Grid.SetRowSpan(VideoContainer, 5);
        }
        else
        {
            _isFullscreen = false;
            Grid.SetRow(VideoContainer, 2);
            Grid.SetRowSpan(VideoContainer, 1);
            CatalogScroll.Visibility = Visibility.Visible;
            MainContentGrid.Margin = new Thickness(28);
            VideoContainer.BorderThickness = new Thickness(1);
            VideoContainer.CornerRadius = new CornerRadius(14);
            VideoContainer.Padding = new Thickness(4);
            VideoContainer.Margin = new Thickness(0, 0, 0, 14);
            WindowStyle = WindowStyle.SingleBorderWindow;
            ResizeMode = ResizeMode.CanResize;
            WindowState = WindowState.Normal;
        }
    }


    private void UpdateClock()
    {
        var now = DateTime.Now;
        ClockTime.Text = now.ToString("HH:mm", CultureInfo.GetCultureInfo("es-AR"));
        ClockDate.Text = now.ToString("ddd, dd MMM yyyy", CultureInfo.GetCultureInfo("es-AR"));
    }

    private void QuickNavigation_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement element || element.Tag is not string target) return;
        var item = Sections.Items.Cast<ListBoxItem>().FirstOrDefault(x => string.Equals(x.Tag?.ToString(), target, StringComparison.OrdinalIgnoreCase));
        if (item is not null) Sections.SelectedItem = item;
    }

    private void OpenNotifications_Click(object sender, RoutedEventArgs e) => StatusText.Text = "No hay un servicio de notificaciones push conectado en esta compilación.";
    private void OpenSettings_Click(object sender, RoutedEventArgs e) => QuickSelect("Configuración");
    private void SearchYouTubeMusic_Click(object sender, RoutedEventArgs e) => StatusText.Text = "La música se reproduce dentro de MILTVPLUS cuando hay una pista directa disponible.";
    private void SearchSpotify_Click(object sender, RoutedEventArgs e) => StatusText.Text = "La música se reproduce dentro de MILTVPLUS cuando hay una pista directa disponible.";

    private void OpenAccount_Click(object sender, RoutedEventArgs e) =>
        MessageBox.Show(this, "La gestión de cuenta y sincronización está disponible en el cliente Android. Esta compilación de escritorio usa el catálogo público y fuentes locales.", "MILTVPLUS · Cuenta", MessageBoxButton.OK, MessageBoxImage.Information);

    private void QuickSelect(string tag)
    {
        var item = Sections.Items.Cast<ListBoxItem>().FirstOrDefault(x => x.Tag?.ToString() == tag);
        if (item is not null) Sections.SelectedItem = item;
    }

    private void AdultCategory_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement element && element.Tag is string category)
        {
            SearchBox.Text = category == "Solo Adultos" ? string.Empty : category;
            StatusText.Text = category == "Solo Adultos" ? "Mostrando todas las publicaciones clasificadas +18." : "Filtrando el catálogo +18 por " + category + ".";
            ApplySearch();
        }
    }

    private async void AdultUnlock_Click(object sender, RoutedEventArgs e)
    {
        if (AdultAgeConfirm.IsChecked != true)
        {
            AdultGateStatus.Text = "Primero confirmá que tenés 18 años o más.";
            return;
        }
        var pin = AdultPinBox.Password;
        if (pin.Length != 6 || pin.Any(ch => !char.IsDigit(ch)))
        {
            AdultGateStatus.Text = "El PIN debe contener exactamente seis números.";
            return;
        }
        try
        {
            Directory.CreateDirectory(AdultDataDirectory);
            File.WriteAllText(Path.Combine(AdultDataDirectory, "adult-age-confirmed"), DateTimeOffset.UtcNow.ToString("O"));
            var pinPath = Path.Combine(AdultDataDirectory, "adult-pin.pbkdf2");
            if (!File.Exists(pinPath))
            {
                var salt = RandomNumberGenerator.GetBytes(16);
                var derived = Rfc2898DeriveBytes.Pbkdf2(pin, salt, 150_000, HashAlgorithmName.SHA256, 32);
                File.WriteAllText(pinPath, Convert.ToHexString(salt) + ":" + Convert.ToHexString(derived));
                AdultGateStatus.Text = "PIN parental creado. Sección desbloqueada.";
            }
            else
            {
                var stored = (await File.ReadAllTextAsync(pinPath)).Trim().Split(':');
                if (stored.Length != 2) throw new InvalidDataException("El archivo de PIN parental no es válido.");
                var salt = Convert.FromHexString(stored[0]);
                var saved = Convert.FromHexString(stored[1]);
                var derived = Rfc2898DeriveBytes.Pbkdf2(pin, salt, 150_000, HashAlgorithmName.SHA256, 32);
                if (!CryptographicOperations.FixedTimeEquals(saved, derived))
                {
                    AdultGateStatus.Text = "PIN incorrecto. La sección permanece bloqueada.";
                    AdultPinBox.Clear();
                    return;
                }
                AdultGateStatus.Text = "Acceso +18 confirmado.";
            }
            _adultUnlocked = true;
            AdultPinBox.Clear();
            AdultGatePanel.Visibility = Visibility.Collapsed;
            AdultPanel.Visibility = Visibility.Visible;
            VideoContainer.Visibility = Visibility.Collapsed;
            CatalogScroll.Visibility = Visibility.Visible;
            Grid.SetRow(CatalogScroll, 3);
            Grid.SetRowSpan(CatalogScroll, 2);
            table = "catalog_items";
            select = "id,title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url";
            order = "created_at.desc";
            kind = null;
            await LoadCatalog();
            PageTitle.Text = "Adultos 18+";
        }
        catch (Exception ex)
        {
            AdultGateStatus.Text = "No se pudo validar el acceso: " + ex.Message;
        }
    }

    private static bool IsAdultRating(string? rating)
    {
        var value = (rating ?? string.Empty).Trim().ToUpperInvariant();
        return value.Contains("18") || new[] { "NC-17", "R18", "TV-MA", "MA", "AO", "ADULT", "ADULTS ONLY" }.Contains(value);
    }


    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        var key = e.Key == Key.System ? e.SystemKey : e.Key;
        var modifiers = Keyboard.Modifiers;
        var typing = Keyboard.FocusedElement is TextBoxBase || Keyboard.FocusedElement is PasswordBox ||
                     Keyboard.FocusedElement is System.Windows.Controls.ComboBox;

        if (key == Key.F11) { ToggleFullscreen(); e.Handled = true; return; }
        if (key == Key.Escape && _isFullscreen) { ToggleFullscreen(); e.Handled = true; return; }

        // Global transport/navigation shortcuts are disabled while editing text or selecting filters.
        if (typing) return;

        if (modifiers == ModifierKeys.Control && key == Key.Right) { ChangeChannel(1); e.Handled = true; }
        else if (modifiers == ModifierKeys.Control && key == Key.Left) { ChangeChannel(-1); e.Handled = true; }
        else if (modifiers == ModifierKeys.Control && key == Key.O) { ImportM3u_Click(this, new RoutedEventArgs()); e.Handled = true; }
        else if (modifiers == ModifierKeys.Control && key == Key.R) { Refresh_Click(this, new RoutedEventArgs()); e.Handled = true; }
        else if (modifiers == ModifierKeys.Control && key == Key.F) { SearchBox.Focus(); SearchBox.SelectAll(); e.Handled = true; }
        else if (modifiers == ModifierKeys.None && key == Key.Space && _currentEntry is not null)
        {
            if (_mediaPlayer.IsPlaying) _mediaPlayer.Pause();
            else _mediaPlayer.Play();
            e.Handled = true;
        }
        else if (modifiers == ModifierKeys.None && key == Key.M)
        {
            _mediaPlayer.Mute = !_mediaPlayer.Mute;
            StatusText.Text = _mediaPlayer.Mute ? "Audio silenciado." : "Audio activado.";
            e.Handled = true;
        }
    }

    private async void ImportM3u_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog
        {
            Title = "Importar lista M3U/M3U8",
            Filter = "Listas M3U (*.m3u;*.m3u8)|*.m3u;*.m3u8|Todos los archivos (*.*)|*.*",
            CheckFileExists = true
        };
        if (dialog.ShowDialog(this) != true) return;

        try
        {
            var info = new FileInfo(dialog.FileName);
            if (info.Length > 5 * 1024 * 1024)
                throw new InvalidDataException("La lista supera el límite permitido de 5 MB.");

            var lines = await File.ReadAllLinesAsync(dialog.FileName);
            var imported = new List<CatalogEntry>();
            var pendingTitle = "Canal importado";
            foreach (var raw in lines)
            {
                var line = raw.Trim();
                if (line.StartsWith("#EXTINF", StringComparison.OrdinalIgnoreCase))
                {
                    var comma = line.LastIndexOf(',');
                    pendingTitle = comma >= 0 && comma < line.Length - 1 ? line[(comma + 1)..].Trim() : "Canal importado";
                    continue;
                }
                if (line.Length == 0 || line.StartsWith('#')) continue;
                if (!Uri.TryCreate(line, UriKind.Absolute, out var streamUri) || streamUri.Scheme != Uri.UriSchemeHttps)
                    continue;
                imported.Add(new CatalogEntry { Title = pendingTitle, Description = "Fuente importada localmente; verificá que tengas autorización para reproducirla.", StreamUrl = streamUri.ToString(), Kind = "live", IsUserProvidedSource = true });
                pendingTitle = "Canal importado";
            }

            entries = imported;
            _localPlaylistLoaded = true;
            UpdateFilterOptions();
            PageTitle.Text = "Lista local importada";
            StatusText.Text = imported.Count == 0
                ? "No se encontraron enlaces HTTPS válidos en la lista."
                : $"{imported.Count} entradas HTTPS importadas. Verificá que tengas derecho a reproducirlas.";
        }
        catch (Exception ex)
        {
            StatusText.Text = "No se pudo importar la lista: " + ex.Message;
        }
    }

    private void MusicPause_Click(object sender, RoutedEventArgs e)
    {
        if (_currentMedia is null)
        {
            StatusText.Text = "Todavía no hay música en reproducción.";
            return;
        }
        _mediaPlayer.Pause();
        StatusText.Text = _mediaPlayer.IsPlaying ? "Música reanudada." : "Música pausada.";
    }

    private void Stop_Click(object sender, RoutedEventArgs e)
    {
        _mediaPlayer.Stop();
        _currentMedia?.Dispose();
        _currentMedia = null;
        _currentEntry = null;
        StatusText.Text = "Reproducción detenida.";
    }

    private void DisposePlayer()
    {
        _mediaPlayer.Stop();
        _currentMedia?.Dispose();
        _mediaPlayer.Dispose();
        _libVlc.Dispose();
    }

    private void MoreOptions_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (sender is ListBoxItem item && item.ContextMenu is ContextMenu menu)
        {
            menu.PlacementTarget = item;
            menu.IsOpen = true;
            e.Handled = true;
        }
    }

    private void NavigateMoreSection(string tag)
    {
        var destination = Sections.Items.OfType<ListBoxItem>().FirstOrDefault(item => item.Tag?.ToString() == tag);
        if (destination is not null) Sections.SelectedItem = destination;
    }

    private void MoreEpg_Click(object sender, RoutedEventArgs e) => NavigateMoreSection("Guía EPG");
    private void MoreCountries_Click(object sender, RoutedEventArgs e) => NavigateMoreSection("Países");
    private void MoreRadios_Click(object sender, RoutedEventArgs e) => NavigateMoreSection("Radios");
    private void MoreTheater_Click(object sender, RoutedEventArgs e) => NavigateMoreSection("Teatro");

    private async void Sections_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded || Sections.SelectedItem is not ListBoxItem item) return;
        var section = item.Tag?.ToString() ?? item.Content?.ToString();
        _localPlaylistLoaded = false;
        _contentCategoryFilter = section == "Infantil" ? "kids" : section == "Documentales" ? "documentary" : null;
        HomePanel.Visibility = section == "Inicio" ? Visibility.Visible : Visibility.Collapsed;
        MusicPanel.Visibility = section == "Música" ? Visibility.Visible : Visibility.Collapsed;
        SettingsPanel.Visibility = section == "Configuración" ? Visibility.Visible : Visibility.Collapsed;
        var isAdult = section == "Adultos 18+";
        AdultGatePanel.Visibility = isAdult && !_adultUnlocked ? Visibility.Visible : Visibility.Collapsed;
        AdultPanel.Visibility = isAdult && _adultUnlocked ? Visibility.Visible : Visibility.Collapsed;
        VideoContainer.Visibility = isAdult || section == "Música" || section == "Configuración" ? Visibility.Collapsed : Visibility.Visible;
        CatalogScroll.Visibility = section == "Configuración" || isAdult ? Visibility.Collapsed : Visibility.Visible;
        Grid.SetRow(CatalogScroll, isAdult || section == "Música" ? 3 : 4);
        Grid.SetRowSpan(CatalogScroll, isAdult || section == "Música" ? 2 : 1);
        if (section == "Configuración")
        {
            entries = [];
            CatalogScroll.ItemsSource = entries;
            PageTitle.Text = "Configuración";
            StatusText.Text = "Administrá actualizaciones, comportamiento de ventana y volumen de reproducción.";
            return;
        }
        if (isAdult)
        {
            entries = [];
            CatalogScroll.ItemsSource = entries;
            PageTitle.Text = "Contenido Adulto";
            StatusText.Text = "Catálogo +18 vacío y protegido. No se consultan ni se cargan URLs activas.";
            return;
        }
        if (isAdult && !_adultUnlocked)
        {
            entries = [];
            CatalogScroll.ItemsSource = entries;
            PageTitle.Text = "Adultos 18+ · Acceso protegido";
            StatusText.Text = "Confirmá tu edad y validá tu PIN parental para continuar.";
            return;
        }
        (table, select, order, kind) = section switch
        {
            "Inicio" or "TV en vivo" or "Países" => ("tv_channels", "id,title,description,country_code,created_at,category,language_code,access_model,logo_url,official_watch_url,stream_url,playback_type,is_free_to_air,source_status,last_checked_at,last_check_detail", "sort_order.asc,title.asc", null),
            "Películas" => ("catalog_items", "id,title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", "movie"),
            "Series" => ("catalog_items", "id,title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", "series"),
            "Infantil" => ("catalog_items", "id,title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", null),
            "Documentales" => ("catalog_items", "id,title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", null),
            "Adultos 18+" => ("catalog_items", "id,title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", null),
            "Deportes" => ("sports_events", "id,title,description,country_code,starts_at,competition,sport,poster_url,official_info_url", "starts_at.asc", null),
            "Música" => ("music_items", "id,title,creator,country_code,created_at,item_type,album,genre,release_year,external_url,artwork_url,stream_url", "created_at.desc", null),
            "Radios" => ("radio_stations", "id,title,description,country_code,created_at,genre,language_code,logo_url,stream_url,website_url", "created_at.desc", null),
            "Teatro" => ("theater_events", "id,title,description,country_code,starts_at,venue,genre,poster_url,info_url", "starts_at.asc", null),
            "Guía EPG" => ("epg_programs", "id,title,description,country_code,starts_at,ends_at,category,language_code,channel_external_id", "starts_at.asc", null),
            "Favoritos" => ("tv_channels", "id,title,description,country_code,created_at,category,language_code,access_model,logo_url,official_watch_url,stream_url,playback_type,is_free_to_air,source_status,last_checked_at,last_check_detail", "sort_order.asc,title.asc", null),
            "Configuración" => ("tv_channels", "id,title,description,country_code,created_at,category,language_code,access_model,logo_url,official_watch_url,stream_url,playback_type,is_free_to_air,source_status,last_checked_at,last_check_detail", "sort_order.asc,title.asc", null),
            _ => ("tv_channels", "id,title,description,country_code,created_at,category,language_code,access_model,logo_url,official_watch_url,stream_url,playback_type,is_free_to_air,source_status,last_checked_at,last_check_detail", "sort_order.asc,title.asc", null)
        };
        PageTitle.Text = section ?? "MILTVPLUS";
        if (section == "Configuración") StatusText.Text = "Pantalla completa: F11 · Cambio de canal: Ctrl + ← / →.";
        if (section == "Favoritos") StatusText.Text = "Los favoritos sincronizados se administran desde la cuenta MILTVPLUS.";
        if (section == "Más Opciones") StatusText.Text = "Más opciones: Fuentes M3U, Control parental +18 y Soporte.";
        await LoadCatalog();
    }

    private sealed class DesktopSettings
    {
        public DesktopSettings() { }
        public bool AutoCheckUpdates { get; set; } = true;
        public bool AlwaysOnTop { get; set; }
        public bool StartMaximized { get; set; }
        public int MusicVolume { get; set; } = 100;
    }

    private sealed class CatalogEntry
    {
        [JsonPropertyName("id")] public string? Id { get; set; }
        public string? Title { get; set; }
        public string? Description { get; set; }
        [JsonPropertyName("country_code")] public string? CountryCode { get; set; }
        public string? Kind { get; set; }
        public string? Genre { get; set; }
        public string? Creator { get; set; }
        public string? Competition { get; set; }
        public string? Sport { get; set; }
        [JsonPropertyName("item_type")] public string? ItemType { get; set; }
        public string? Album { get; set; }
        [JsonPropertyName("release_year")] public int? ReleaseYear { get; set; }
        [JsonPropertyName("age_rating")] public string? AgeRating { get; set; }
        [JsonPropertyName("starts_at")] public DateTimeOffset? StartsAt { get; set; }
        [JsonPropertyName("ends_at")] public DateTimeOffset? EndsAt { get; set; }
        [JsonPropertyName("channel_external_id")] public string? ChannelExternalId { get; set; }
        [JsonPropertyName("rights_verified")] public bool RightsVerified { get; set; }
        [JsonPropertyName("rights_holder")] public string? RightsHolder { get; set; }
        [JsonPropertyName("rights_reference")] public string? RightsReference { get; set; }
        [JsonPropertyName("rights_regions")] public string[]? RightsRegions { get; set; }
        [JsonPropertyName("rights_valid_from")] public DateTimeOffset? RightsValidFrom { get; set; }
        [JsonPropertyName("rights_valid_until")] public DateTimeOffset? RightsValidUntil { get; set; }
        public bool IsUserProvidedSource { get; set; }
        public bool HasActivePlaybackRights
        {
            get
            {
                var region = RegionInfo.CurrentRegion.TwoLetterISORegionName;
                var now = DateTimeOffset.UtcNow;
                return RightsVerified && !string.IsNullOrWhiteSpace(RightsHolder) &&
                    !string.IsNullOrWhiteSpace(RightsReference) && !string.IsNullOrWhiteSpace(region) &&
                    RightsRegions?.Any(x => string.Equals(x, region, StringComparison.OrdinalIgnoreCase) ||
                        x == "001" || x == "*") == true &&
                    RightsValidFrom.HasValue && RightsValidFrom.Value <= now &&
                    (!RightsValidUntil.HasValue || RightsValidUntil.Value > now) &&
                    (!string.Equals(PlaybackType, "hls", StringComparison.OrdinalIgnoreCase) &&
                     !string.Equals(PlaybackType, "dash", StringComparison.OrdinalIgnoreCase) ||
                     string.Equals(SourceStatus, "online", StringComparison.OrdinalIgnoreCase) || string.IsNullOrWhiteSpace(SourceStatus));
            }
        }
        [JsonPropertyName("stream_url")] public string? StreamUrl { get; set; }
        [JsonPropertyName("authorized_stream_url")] public string? AuthorizedStreamUrl { get; set; }
        [JsonPropertyName("external_url")] public string? ExternalUrl { get; set; }
        [JsonPropertyName("artwork_url")] public string? ArtworkUrl { get; set; }
        [JsonPropertyName("official_watch_url")] public string? OfficialWatchUrl { get; set; }
        [JsonPropertyName("logo_url")] public string? LogoUrl { get; set; }
        public string? Category { get; set; }
        [JsonPropertyName("language_code")] public string? LanguageCode { get; set; }
        [JsonPropertyName("playback_type")] public string? PlaybackType { get; set; }
        [JsonPropertyName("is_free_to_air")] public bool? IsFreeToAir { get; set; }
        [JsonPropertyName("source_status")] public string? SourceStatus { get; set; }
        [JsonPropertyName("last_checked_at")] public DateTimeOffset? LastCheckedAt { get; set; }
        [JsonPropertyName("last_check_detail")] public string? LastCheckDetail { get; set; }
        private string? LastCheckSummary => LastCheckDetail is { Length: > 150 } ? LastCheckDetail[..150] : LastCheckDetail;
        private string? LiveStatusLabel => LastCheckDetail?.StartsWith("YouTube confirma", StringComparison.OrdinalIgnoreCase) == true ? "EN VIVO verificado" : null;
        [JsonPropertyName("access_model")] public string? AccessModel { get; set; }
        private string? AccessModelLabel => AccessModel switch
        {
            "free_web" => "Acceso web gratuito",
            "free_to_air" => "Televisión abierta",
            "free_with_ads" => "Gratis con anuncios",
            "subscription" => "Suscripción",
            "geo_restricted" => "Con restricción geográfica",
            _ => null
        };
        public string Metadata => string.Join(" · ", new[] { Category ?? Kind ?? ItemType ?? Sport, Genre ?? Competition ?? Creator, CountryCode, LanguageCode, AccessModelLabel, PlaybackType, LiveStatusLabel, SourceStatus, LastCheckSummary, LastCheckedAt?.ToLocalTime().ToString("g"), ReleaseYear?.ToString(), AgeRating, StartsAt?.ToLocalTime().ToString("g") }.Where(x => !string.IsNullOrWhiteSpace(x)));
    }
}