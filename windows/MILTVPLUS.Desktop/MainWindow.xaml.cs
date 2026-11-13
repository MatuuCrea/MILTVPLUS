using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;

namespace MILTVPLUS.Desktop;

public partial class MainWindow : Window
{
    private const string Api = "https://rbnuxapfsnaabrzuxvdy.supabase.co/rest/v1/";
    private const string PublicKey = "sb_publishable_tmBU-IB7P0db0w7RG9TMEw_GD754hb7";
    private static readonly HttpClient Client = new();
    private List<CatalogEntry> entries = [];
    private string table = "catalog_items";
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
            var query = "?select=title,description,kind,genre,country_code,is_published&is_published=eq.true&order=created_at.desc&limit=100";
            if (kind is not null) query += "&kind=eq." + Uri.EscapeDataString(kind);
            using var response = await Client.GetAsync(Api + table + query);
            response.EnsureSuccessStatusCode();
            await using var stream = await response.Content.ReadAsStreamAsync();
            var rows = await JsonSerializer.DeserializeAsync<List<CatalogEntry>>(stream, new JsonSerializerOptions { PropertyNameCaseInsensitive = true }) ?? [];
            entries = rows;
            ApplySearch();
            StatusText.Text = $"{entries.Count} publicaciones · Sólo contenido publicado";
        }
        catch (Exception ex)
        {
            StatusText.Text = "No se pudo conectar: " + ex.Message;
            Catalog.ItemsSource = Array.Empty<CatalogEntry>();
        }
    }

    private void ApplySearch()
    {
        var term = SearchBox.Text.Trim();
        Catalog.ItemsSource = entries.Where(x => string.IsNullOrEmpty(term) ||
            (x.Title + " " + x.Description + " " + x.Genre).Contains(term, StringComparison.OrdinalIgnoreCase)).ToList();
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e) => await LoadCatalog();
    private void SearchBox_TextChanged(object sender, TextChangedEventArgs e) { if (IsLoaded) ApplySearch(); }

    private async void Sections_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded || Sections.SelectedItem is not ListBoxItem item) return;
        (table, kind) = item.Content?.ToString() switch
        {
            "TV en vivo" => ("catalog_items", "live"),
            "Películas" => ("catalog_items", "movie"),
            "Series" => ("catalog_items", "series"),
            "Deportes" => ("sports_events", null),
            "Radios" => ("radio_stations", null),
            "Música" => ("music_items", null),
            "Teatro" => ("theater_events", null),
            "Guía EPG" => ("epg_programs", null),
            _ => ("catalog_items", null)
        };
        PageTitle.Text = item.Content?.ToString() ?? "MILTVPLUS";
        await LoadCatalog();
    }

    private sealed class CatalogEntry
    {
        public string? Title { get; set; }
        public string? Description { get; set; }
        public string? Kind { get; set; }
        public string? Genre { get; set; }
        public string? Country_Code { get; set; }
        public string Metadata => string.Join(" · ", new[] { Kind, Genre, Country_Code }.Where(x => !string.IsNullOrWhiteSpace(x)));
    }
}