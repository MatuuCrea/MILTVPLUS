using System.Net.Http;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Windows;
using System.Windows.Controls;

namespace MILTVPLUS.Desktop;

public partial class MainWindow : Window
{
    private const string Api = "https://rbnuxapfsnaabrzuxvdy.supabase.co/rest/v1/";
    private const string PublicKey = "sb_publishable_tmBU-IB7P0db0w7RG9TMEw_GD754hb7";
    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(20) };
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private List<CatalogEntry> entries = [];
    private string table = "catalog_items";
    private string select = "title,description,country_code,created_at,kind,genre,release_year,age_rating,poster_url,stream_url";
    private string order = "created_at.desc";
    private string? kind;

    public MainWindow()
    {
        InitializeComponent();
        Client.DefaultRequestHeaders.TryAddWithoutValidation("apikey", PublicKey);
        Client.DefaultRequestHeaders.TryAddWithoutValidation("Accept", "application/json");
        Loaded += async (_, _) => await LoadCatalog();
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
            ApplySearch();
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

    private void ApplySearch()
    {
        var term = SearchBox.Text.Trim();
        Catalog.ItemsSource = entries.Where(x => string.IsNullOrEmpty(term) ||
            (x.Title + " " + x.Description + " " + x.Genre + " " + x.Creator + " " + x.CountryCode)
            .Contains(term, StringComparison.OrdinalIgnoreCase)).ToList();
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e) => await LoadCatalog();
    private void SearchBox_TextChanged(object sender, TextChangedEventArgs e) { if (IsLoaded) ApplySearch(); }

    private async void Sections_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded || Sections.SelectedItem is not ListBoxItem item) return;
        var section = item.Content?.ToString();
        (table, select, order, kind) = section switch
        {
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
        public string Metadata => string.Join(" · ", new[] { Kind ?? ItemType ?? Sport, Genre ?? Competition ?? Creator, CountryCode, ReleaseYear?.ToString(), AgeRating, StartsAt?.ToLocalTime().ToString("g") }.Where(x => !string.IsNullOrWhiteSpace(x)));
    }
}