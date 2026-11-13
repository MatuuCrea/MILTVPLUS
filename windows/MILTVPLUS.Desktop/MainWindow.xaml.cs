using System.Net.Http;
using System.Reflection;
using System.IO;
using System.Diagnostics;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;
using LibVLCSharp.Shared;

namespace MILTVPLUS.Desktop;

public partial class MainWindow : Window
{
    private const string Api = "https://rbnuxapfsnaabrzuxvdy.supabase.co/rest/v1/";
    private const string PublicKey = "sb_publishable_tmBU-IB7P0db0w7RG9TMEw_GD754hb7";
    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(20) };
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private readonly LibVLC _libVlc;
    private readonly MediaPlayer _mediaPlayer;
    private Media? _currentMedia;
    private List<CatalogEntry> entries = [];
    private string table = "catalog_items";
    private string select = "title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url";
    private string order = "created_at.desc";
    private string? kind;
    private UpdateRelease? _availableUpdate;
    private bool _updatingFilters;

    public MainWindow()
    {
        Core.Initialize();
        _libVlc = new LibVLC("--no-video-title-show", "--network-caching=1500");
        _mediaPlayer = new MediaPlayer(_libVlc);
        InitializeComponent();
        VideoSurface.MediaPlayer = _mediaPlayer;
        Client.DefaultRequestHeaders.TryAddWithoutValidation("apikey", PublicKey);
        Client.DefaultRequestHeaders.TryAddWithoutValidation("Accept", "application/json");
        Loaded += async (_, _) => { await LoadCatalog(); await CheckForUpdates(silent: true); };
        Closed += (_, _) => DisposePlayer();
    }

    private async Task LoadCatalog()
    {
        StatusText.Text = "Consultando publicaciones autorizadas…";
        try
        {
            var query = $"?select={Uri.EscapeDataString(select)}&is_published=eq.true&order={Uri.EscapeDataString(order)}&limit=100";
            if (kind is not null) query += "&kind=eq." + Uri.EscapeDataString(kind);
            using var response = await Client.GetAsync(Api + table + query);
            if (!response.IsSuccessStatusCode)
            {
                var detail = await response.Content.ReadAsStringAsync();
                throw new HttpRequestException($"Supabase {(int)response.StatusCode}: {detail}");
            }
            await using var stream = await response.Content.ReadAsStreamAsync();
            entries = await JsonSerializer.DeserializeAsync<List<CatalogEntry>>(stream, JsonOptions) ?? [];
            UpdateFilterOptions();
            StatusText.Text = entries.Count == 0
                ? "Todavía no hay contenido autorizado publicado en esta sección."
                : $"{entries.Count} publicaciones · Contenido publicado";
        }
        catch (Exception ex)
        {
            StatusText.Text = "No se pudo cargar el catálogo: " + ex.Message;
            entries = [];
            Catalog.ItemsSource = entries;
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
        Catalog.ItemsSource = entries.Where(x =>
            (string.IsNullOrEmpty(term) ||
                (x.Title + " " + x.Description + " " + x.Genre + " " + x.Creator + " " + x.CountryCode + " " + x.Category + " " + x.LanguageCode + " " + x.SourceStatus + " " + x.AccessModel)
                .Contains(term, StringComparison.OrdinalIgnoreCase)) &&
            (string.IsNullOrEmpty(country) || country == "Todos" || string.Equals(x.CountryCode, country, StringComparison.OrdinalIgnoreCase)) &&
            (string.IsNullOrEmpty(category) || category == "Todas" || string.Equals(x.Category, category, StringComparison.OrdinalIgnoreCase))
        ).ToList();
    }

    private void Filter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (IsLoaded && !_updatingFilters) ApplySearch();
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
        if ((sender as FrameworkElement)?.DataContext is not CatalogEntry item)
            return;

        if (!Uri.TryCreate(item.StreamUrl, UriKind.Absolute, out var uri) ||
            uri.Scheme != Uri.UriSchemeHttps)
        {
            if (Uri.TryCreate(item.OfficialWatchUrl, UriKind.Absolute, out var officialUri) &&
                officialUri.Scheme == Uri.UriSchemeHttps)
            {
                try
                {
                    Process.Start(new ProcessStartInfo(officialUri.ToString()) { UseShellExecute = true });
                    StatusText.Text = "Abriendo la señal en el reproductor oficial: " + item.Title;
                }
                catch (Exception ex) { StatusText.Text = "No se pudo abrir el canal oficial: " + ex.Message; }
                return;
            }
            StatusText.Text = "Este canal todavía no tiene una señal HTTPS verificada ni una página oficial de visualización.";
            return;
        }

        try
        {
            _currentMedia?.Dispose();
            _currentMedia = new Media(_libVlc, uri);
            if (!_mediaPlayer.Play(_currentMedia))
                throw new InvalidOperationException("El reproductor no pudo iniciar el flujo.");
            StatusText.Text = "Reproduciendo: " + item.Title;
        }
        catch (Exception ex)
        {
            StatusText.Text = "Error de reproducción: " + ex.Message;
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
                imported.Add(new CatalogEntry { Title = pendingTitle, Description = "Fuente importada localmente", StreamUrl = streamUri.ToString(), Kind = "live" });
                pendingTitle = "Canal importado";
            }

            entries = imported;
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

    private void Stop_Click(object sender, RoutedEventArgs e)
    {
        _mediaPlayer.Stop();
        _currentMedia?.Dispose();
        _currentMedia = null;
        StatusText.Text = "Reproducción detenida.";
    }

    private void DisposePlayer()
    {
        _mediaPlayer.Stop();
        _currentMedia?.Dispose();
        _mediaPlayer.Dispose();
        _libVlc.Dispose();
    }

    private async void Sections_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded || Sections.SelectedItem is not ListBoxItem item) return;
        var section = item.Content?.ToString();
        (table, select, order, kind) = section switch
        {
            "Canales por país" => ("tv_channels", "title,description,country_code,created_at,category,language_code,access_model,logo_url,official_watch_url,stream_url,playback_type,is_free_to_air,source_status", "sort_order.asc,title.asc", null),
            "TV en vivo" => ("catalog_items", "title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", "live"),
            "Películas" => ("catalog_items", "title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", "movie"),
            "Series" => ("catalog_items", "title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", "series"),
            "Deportes" => ("sports_events", "title,description,country_code,starts_at,competition,sport,poster_url,official_info_url", "starts_at.asc", null),
            "Radios" => ("radio_stations", "title,description,country_code,created_at,genre,language_code,logo_url,stream_url,website_url", "created_at.desc", null),
            "Música" => ("music_items", "title,creator,country_code,created_at,item_type,album,genre,release_year,external_url,artwork_url", "created_at.desc", null),
            "Teatro" => ("theater_events", "title,description,country_code,starts_at,venue,genre,poster_url,info_url", "starts_at.asc", null),
            "Guía EPG" => ("epg_programs", "title,description,country_code,starts_at,ends_at,category,language_code,channel_external_id", "starts_at.asc", null),
            _ => ("catalog_items", "title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url", "created_at.desc", null)
        };
        PageTitle.Text = section ?? "MILTVPLUS";
        await LoadCatalog();
    }

    private sealed class CatalogEntry
    {
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
        [JsonPropertyName("stream_url")] public string? StreamUrl { get; set; }
        [JsonPropertyName("official_watch_url")] public string? OfficialWatchUrl { get; set; }
        [JsonPropertyName("logo_url")] public string? LogoUrl { get; set; }
        public string? Category { get; set; }
        [JsonPropertyName("language_code")] public string? LanguageCode { get; set; }
        [JsonPropertyName("playback_type")] public string? PlaybackType { get; set; }
        [JsonPropertyName("is_free_to_air")] public bool? IsFreeToAir { get; set; }
        [JsonPropertyName("source_status")] public string? SourceStatus { get; set; }
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
        public string Metadata => string.Join(" · ", new[] { Category ?? Kind ?? ItemType ?? Sport, Genre ?? Competition ?? Creator, CountryCode, LanguageCode, AccessModelLabel, PlaybackType, SourceStatus, ReleaseYear?.ToString(), AgeRating, StartsAt?.ToLocalTime().ToString("g") }.Where(x => !string.IsNullOrWhiteSpace(x)));
    }
}