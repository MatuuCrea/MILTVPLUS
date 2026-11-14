using System.Diagnostics;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.IO;
using System.Windows;
using System.Windows.Controls;

namespace MILTVPLUS.Desktop;

public partial class TmdbCatalogWindow : Window
{
    private const string SupabaseUrl = "https://rbnuxapfsnaabrzuxvdy.supabase.co";
    private const string PublicKey = "sb_publishable_tmBU-IB7P0db0w7RG9TMEw_GD754hb7";
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(25) };
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private readonly bool _isSeries;
    private string? _accessToken;
    private string? _refreshToken;
    private TmdbItem? _selected;
    private JsonElement? _details;
    private bool _loadingSeason;
    private readonly HashSet<string> _favorites = [];
    private string FavoritesPath => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MILTVPLUS", "tmdb-favorites.json");

    public TmdbCatalogWindow(bool isSeries)
    {
        _isSeries = isSeries;
        InitializeComponent();
        HeaderTitle.Text = isSeries ? "Series" : "Películas";
        TopRatedButton.Visibility = isSeries ? Visibility.Collapsed : Visibility.Visible;
        UpcomingButton.Visibility = isSeries ? Visibility.Collapsed : Visibility.Visible;
        AiringButton.Visibility = isSeries ? Visibility.Visible : Visibility.Collapsed;
        LoadFavorites();
        Loaded += async (_, _) => await InitializeTrailerView();
        UpdateAuthUi();
        UpdateDetailEmpty();
    }

    private async Task InitializeTrailerView()
    {
        try { await TrailerView.EnsureCoreWebView2Async(); }
        catch { TrailerNotice.Text = "No se pudo inicializar el reproductor web del tráiler."; }
    }

    private async void SignIn_Click(object sender, RoutedEventArgs e)
    {
        var email = EmailBox.Text.Trim();
        var password = PasswordBox.Password;
        if (email.Length < 5 || !email.Contains('@') || password.Length < 8)
        {
            StatusText.Text = "Ingresá un correo válido y la contraseña de tu cuenta MILTVPLUS.";
            return;
        }
        SignInButton.IsEnabled = false;
        StatusText.Text = "Autenticando con Supabase…";
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Post, SupabaseUrl + "/auth/v1/token?grant_type=password");
            request.Headers.TryAddWithoutValidation("apikey", PublicKey);
            request.Content = new StringContent(JsonSerializer.Serialize(new { email, password }), Encoding.UTF8, "application/json");
            using var response = await Http.SendAsync(request);
            if (!response.IsSuccessStatusCode)
            {
                var error = await response.Content.ReadAsStringAsync();
                StatusText.Text = ReadApiError(error, "No se pudo iniciar sesión.");
                return;
            }
            using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            _accessToken = json.RootElement.GetProperty("access_token").GetString();
            _refreshToken = json.RootElement.TryGetProperty("refresh_token", out var refresh) ? refresh.GetString() : null;
            if (string.IsNullOrWhiteSpace(_accessToken)) throw new InvalidOperationException("Supabase no devolvió un token de sesión.");
            PasswordBox.Clear();
            UpdateAuthUi();
            StatusText.Text = "Sesión iniciada. Cargando catálogo…";
            await LoadCategoryAsync("popular");
        }
        catch (Exception ex) { StatusText.Text = "Error de autenticación: " + ex.Message; }
        finally { SignInButton.IsEnabled = true; }
    }

    private void SignOut_Click(object sender, RoutedEventArgs e)
    {
        _accessToken = null;
        _refreshToken = null;
        ResultsList.ItemsSource = null;
        _selected = null;
        _details = null;
        TrailerView.Visibility = Visibility.Collapsed;
        UpdateAuthUi();
        UpdateDetailEmpty();
        StatusText.Text = "Sesión cerrada. El token se eliminó de la memoria de esta ventana.";
    }

    private void UpdateAuthUi()
    {
        var signedIn = !string.IsNullOrWhiteSpace(_accessToken);
        EmailBox.IsEnabled = !signedIn;
        PasswordBox.IsEnabled = !signedIn;
        SignInButton.Visibility = signedIn ? Visibility.Collapsed : Visibility.Visible;
        SignOutButton.Visibility = signedIn ? Visibility.Visible : Visibility.Collapsed;
    }

    private async void Category_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: string category }) await LoadCategoryAsync(category);
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e) => await LoadCategoryAsync("popular");

    private async Task LoadCategoryAsync(string category, string? query = null)
    {
        if (string.IsNullOrWhiteSpace(_accessToken))
        {
            StatusText.Text = "Iniciá sesión para consultar el catálogo seguro de TMDB.";
            return;
        }
        var action = query is not null
            ? (_isSeries ? "search_tv" : "search_movie")
            : category switch
            {
                "top" when !_isSeries => "movie_top_rated",
                "upcoming" when !_isSeries => "movie_upcoming",
                "airing" when _isSeries => "tv_on_the_air",
                _ => _isSeries ? "tv_popular" : "movie_popular"
            };
        var request = new Dictionary<string, object> { ["action"] = action, ["page"] = 1 };
        if (query is not null) request["query"] = query;
        await RunBusy(async () =>
        {
            using var json = await TmdbRequestAsync(request);
            var items = ParseItems(json.RootElement);
            ResultsList.ItemsSource = items;
            StatusText.Text = items.Count == 0 ? "No hay resultados para esta consulta." : $"Se encontraron {items.Count} títulos. Elegí uno para ver su ficha.";
            _selected = null;
            _details = null;
            UpdateDetailEmpty();
        });
    }

    private async void Search_Click(object sender, RoutedEventArgs e)
    {
        var query = SearchBox.Text.Trim();
        if (query.Length < 2 || query.Length > 100)
        {
            StatusText.Text = "La búsqueda debe tener entre 2 y 100 caracteres.";
            return;
        }
        await LoadCategoryAsync("search", query);
    }

    private async void ResultsList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (ResultsList.SelectedItem is not TmdbItem item || string.IsNullOrWhiteSpace(_accessToken)) return;
        _selected = item;
        await LoadDetailsAsync(item);
    }

    private async Task LoadDetailsAsync(TmdbItem item)
    {
        var action = _isSeries ? "tv_details" : "movie_details";
        await RunBusy(async () =>
        {
            using var json = await TmdbRequestAsync(new Dictionary<string, object> { ["action"] = action, ["id"] = item.Id });
            _details = json.RootElement.Clone();
            RenderDetails(json.RootElement, item);
        });
    }

    private void RenderDetails(JsonElement details, TmdbItem item)
    {
        DetailTitle.Text = item.Title;
        DetailMeta.Text = string.Join(" · ", new[] { item.Year, item.RatingLabel, ReadString(details, "runtime") is { Length: > 0 } runtime ? runtime + " min" : null }.Where(x => !string.IsNullOrWhiteSpace(x)));
        OverviewText.Text = ReadString(details, "overview") is { Length: > 0 } overview ? overview : "No hay sinopsis disponible.";
        DetailPoster.Source = ImageSource(item.PosterUrl);
        FavoriteButton.Content = _favorites.Contains(FavoriteKey(item.Id)) ? "♥ Me gusta" : "♡ Me gusta";
        var cast = new List<string>();
        if (details.TryGetProperty("credits", out var credits) && credits.TryGetProperty("cast", out var castArray))
            foreach (var person in castArray.EnumerateArray().Take(8))
                cast.Add($"{ReadString(person, "name")} ({ReadString(person, "character")})");
        CreditsText.Text = cast.Count == 0 ? "Reparto no disponible." : "REPARTO\n" + string.Join(" · ", cast);
        var trailer = FindOfficialTrailer(details);
        if (trailer is not null)
        {
            TrailerNotice.Text = "Tráiler oficial de YouTube";
            TrailerNotice.Visibility = Visibility.Visible;
            TrailerView.Visibility = Visibility.Visible;
            if (TrailerView.CoreWebView2 is not null)
                TrailerView.NavigateToString($"<!doctype html><html><body style='margin:0;background:#050812'><iframe width='100%' height='100%' src='https://www.youtube-nocookie.com/embed/{Uri.EscapeDataString(trailer)}' title='Tráiler oficial' frameborder='0' allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share' allowfullscreen></iframe></body></html>");
        }
        else
        {
            TrailerView.Visibility = Visibility.Collapsed;
            TrailerNotice.Text = "No hay tráiler oficial disponible para este título.";
        }
        var seasons = new List<SeasonItem>();
        if (_isSeries && details.TryGetProperty("seasons", out var seasonArray))
        {
            foreach (var season in seasonArray.EnumerateArray())
            {
                if (!season.TryGetProperty("season_number", out var number) || !number.TryGetInt32(out var seasonNumber)) continue;
                seasons.Add(new SeasonItem(seasonNumber, ReadString(season, "name") ?? $"Temporada {seasonNumber}"));
            }
        }
        SeasonPanel.Visibility = _isSeries ? Visibility.Visible : Visibility.Collapsed;
        SeasonCombo.ItemsSource = seasons;
        SeasonCombo.DisplayMemberPath = "Name";
        SeasonCombo.SelectedValuePath = "Number";
        if (seasons.Count > 0) SeasonCombo.SelectedIndex = 0;
        else EpisodesList.ItemsSource = null;
    }

    private async void SeasonCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_loadingSeason || !_isSeries || SeasonCombo.SelectedValue is not int season || _selected is null || string.IsNullOrWhiteSpace(_accessToken)) return;
        _loadingSeason = true;
        try
        {
            using var json = await TmdbRequestAsync(new Dictionary<string, object> { ["action"] = "tv_season", ["id"] = _selected.Id, ["season"] = season });
            var episodes = new List<EpisodeItem>();
            if (json.RootElement.TryGetProperty("episodes", out var array))
                foreach (var episode in array.EnumerateArray())
                    episodes.Add(new EpisodeItem(
                        $"{ReadString(episode, "episode_number")}. {ReadString(episode, "name")}",
                        ReadString(episode, "overview") ?? "Sin descripción disponible."));
            EpisodesList.ItemsSource = episodes;
        }
        catch (Exception ex) { StatusText.Text = "No se pudieron cargar los episodios: " + ex.Message; }
        finally { _loadingSeason = false; }
    }

    private void Favorite_Click(object sender, RoutedEventArgs e)
    {
        if (_selected is null) return;
        var key = FavoriteKey(_selected.Id);
        if (!_favorites.Add(key)) _favorites.Remove(key);
        SaveFavorites();
        FavoriteButton.Content = _favorites.Contains(key) ? "♥ Me gusta" : "♡ Me gusta";
        StatusText.Text = "Preferencia guardada localmente en esta computadora.";
    }

    private void Share_Click(object sender, RoutedEventArgs e)
    {
        if (_selected is null) return;
        var url = $"https://www.themoviedb.org/{(_isSeries ? "tv" : "movie")}/{_selected.Id}";
        Clipboard.SetText($"{_selected.Title}\nInformación e imágenes: TMDB.\n{url}");
        StatusText.Text = "Enlace copiado. Ya podés pegarlo en cualquier red o aplicación.";
    }

    private async Task<JsonDocument> TmdbRequestAsync(Dictionary<string, object> payload)
    {
        if (string.IsNullOrWhiteSpace(_accessToken)) throw new InvalidOperationException("Iniciá sesión para continuar.");
        using var request = new HttpRequestMessage(HttpMethod.Post, SupabaseUrl + "/functions/v1/tmdb-catalog");
        request.Headers.TryAddWithoutValidation("apikey", PublicKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _accessToken);
        request.Content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
        using var response = await Http.SendAsync(request);
        var body = await response.Content.ReadAsStringAsync();
        if (response.StatusCode == System.Net.HttpStatusCode.Unauthorized && await TryRefreshSessionAsync())
            return await TmdbRequestAsync(payload);
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException(ReadApiError(body, $"TMDB respondió HTTP {(int)response.StatusCode}."));
        return JsonDocument.Parse(body);
    }

    private async Task<bool> TryRefreshSessionAsync()
    {
        if (string.IsNullOrWhiteSpace(_refreshToken)) return false;
        using var request = new HttpRequestMessage(HttpMethod.Post, SupabaseUrl + "/auth/v1/token?grant_type=refresh_token");
        request.Headers.TryAddWithoutValidation("apikey", PublicKey);
        request.Content = new StringContent(JsonSerializer.Serialize(new { refresh_token = _refreshToken }), Encoding.UTF8, "application/json");
        using var response = await Http.SendAsync(request);
        if (!response.IsSuccessStatusCode) { SignOut_Click(this, new RoutedEventArgs()); return false; }
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        _accessToken = json.RootElement.GetProperty("access_token").GetString();
        _refreshToken = json.RootElement.TryGetProperty("refresh_token", out var token) ? token.GetString() : _refreshToken;
        return !string.IsNullOrWhiteSpace(_accessToken);
    }

    private async Task RunBusy(Func<Task> action)
    {
        IsEnabled = false;
        try { await action(); }
        catch (Exception ex) { StatusText.Text = ex.Message; }
        finally { IsEnabled = true; }
    }

    private List<TmdbItem> ParseItems(JsonElement root)
    {
        var result = new List<TmdbItem>();
        if (!root.TryGetProperty("results", out var items) || items.ValueKind != JsonValueKind.Array) return result;
        foreach (var item in items.EnumerateArray())
        {
            var id = item.TryGetProperty("id", out var idElement) && idElement.TryGetInt32(out var parsedId) ? parsedId : 0;
            if (id <= 0) continue;
            var title = ReadString(item, _isSeries ? "name" : "title") ?? "Sin título";
            var date = ReadString(item, _isSeries ? "first_air_date" : "release_date");
            result.Add(new TmdbItem(id, title, date?.Length >= 4 ? date[..4] : "Año no informado",
                ReadString(item, "poster_path") is { Length: > 0 } poster ? "https://image.tmdb.org/t/p/w342" + poster : null,
                ReadString(item, "overview") ?? "", item.TryGetProperty("vote_average", out var rating) && rating.TryGetDouble(out var value) ? $"TMDB ★ {value:0.0}/10" : "Sin valoración"));
        }
        return result;
    }

    private static string? FindOfficialTrailer(JsonElement details)
    {
        if (!details.TryGetProperty("videos", out var videos) || !videos.TryGetProperty("results", out var results)) return null;
        foreach (var video in results.EnumerateArray())
            if (ReadString(video, "site") == "YouTube" && ReadString(video, "type") == "Trailer" &&
                video.TryGetProperty("official", out var official) && official.ValueKind == JsonValueKind.True &&
                ReadString(video, "key") is { Length: > 0 } key) return key;
        return null;
    }

    private static string? ReadString(JsonElement element, string property)
    {
        if (!element.TryGetProperty(property, out var value)) return null;
        return value.ValueKind == JsonValueKind.String ? value.GetString() : value.ValueKind == JsonValueKind.Number ? value.ToString() : null;
    }

    private static System.Windows.Media.Imaging.BitmapImage? ImageSource(string? url)
    {
        if (string.IsNullOrWhiteSpace(url)) return null;
        try
        {
            var image = new System.Windows.Media.Imaging.BitmapImage();
            image.BeginInit(); image.UriSource = new Uri(url); image.CacheOption = System.Windows.Media.Imaging.BitmapCacheOption.OnLoad; image.EndInit();
            return image;
        }
        catch { return null; }
    }

    private void LoadFavorites()
    {
        try
        {
            if (File.Exists(FavoritesPath))
                foreach (var id in JsonSerializer.Deserialize<string[]>(File.ReadAllText(FavoritesPath)) ?? []) _favorites.Add(id);
        }
        catch { _favorites.Clear(); }
    }

    private string FavoriteKey(int id) => $"{(_isSeries ? "tv" : "movie")}_{id}";

    private void SaveFavorites()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(FavoritesPath)!);
        File.WriteAllText(FavoritesPath, JsonSerializer.Serialize(_favorites.Order(StringComparer.Ordinal).ToArray()));
    }

    private void UpdateDetailEmpty()
    {
        DetailTitle.Text = "Elegí una película o serie";
        DetailMeta.Text = "";
        OverviewText.Text = string.IsNullOrWhiteSpace(_accessToken) ? "Iniciá sesión con tu cuenta MILTVPLUS para consultar TMDB." : "Seleccioná un título de la lista.";
        DetailPoster.Source = null;
        CreditsText.Text = "";
        TrailerView.Visibility = Visibility.Collapsed;
        TrailerNotice.Text = "No hay tráiler oficial disponible para este título.";
        SeasonPanel.Visibility = Visibility.Collapsed;
        EpisodesList.ItemsSource = null;
    }

    private static string ReadApiError(string body, string fallback)
    {
        try
        {
            using var json = JsonDocument.Parse(body);
            return json.RootElement.TryGetProperty("msg", out var msg) ? msg.GetString() ?? fallback :
                json.RootElement.TryGetProperty("message", out var message) ? message.GetString() ?? fallback :
                json.RootElement.TryGetProperty("error_description", out var description) ? description.GetString() ?? fallback :
                json.RootElement.TryGetProperty("error", out var error) ? error.GetString() ?? fallback : fallback;
        }
        catch { return fallback; }
    }

    private sealed record TmdbItem(int Id, string Title, string Year, string? PosterUrl, string Overview, string RatingLabel);
    private sealed record SeasonItem(int Number, string Name);
    private sealed record EpisodeItem(string Name, string Overview);
}