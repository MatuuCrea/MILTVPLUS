import json
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]


class SmartTvClientTests(unittest.TestCase):
    def test_tizen_manifest_and_assets_are_present(self):
        root = ET.parse(ROOT / "platforms/tizen/config.xml").getroot()
        ns = {"t": "http://tizen.org/ns/widgets", "w": "http://www.w3.org/ns/widgets"}
        self.assertEqual(root.find("w:content", namespaces=ns).get("src"), "index.html")
        self.assertIsNotNone(root.find("t:profile[@name='tv-samsung']", ns))
        self.assertTrue((ROOT / "platforms/tizen/index.html").is_file())
        self.assertTrue((ROOT / "platforms/tizen/app.js").is_file())
        self.assertTrue((ROOT / "platforms/tizen/icon.png").read_bytes().startswith(bytes.fromhex("89504e470d0a1a0a")))

    def test_webos_manifest_and_assets_are_present(self):
        app = json.loads((ROOT / "platforms/webos/appinfo.json").read_text(encoding="utf-8"))
        self.assertEqual(app["type"], "web")
        self.assertEqual(app["main"], "index.html")
        for name in (app["icon"], app["largeIcon"]):
            self.assertTrue((ROOT / "platforms/webos" / name).is_file())
            self.assertTrue((ROOT / "platforms/webos" / name).read_bytes().startswith(bytes.fromhex("89504e470d0a1a0a")))
        self.assertTrue((ROOT / "platforms/webos/app.js").is_file())

    def test_both_clients_only_query_published_channels(self):
        for path in ("platforms/tizen/app.js", "platforms/webos/app.js"):
            source = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn("/rest/v1/tv_channels?", source)
            self.assertIn("is_published=eq.true", source)
            self.assertIn("sb_publishable_", source)
            self.assertNotIn("service_role", source.lower())

    def test_direct_playback_requires_platform_supported_type_and_https(self):
        tizen = (ROOT / "platforms/tizen/app.js").read_text(encoding="utf-8")
        webos = (ROOT / "platforms/webos/app.js").read_text(encoding="utf-8")
        self.assertIn('c.playback_type==="hls"||c.playback_type==="dash"', tizen)
        self.assertIn('c.playback_type==="hls"', webos)
        self.assertIn('(c.playback_type==="hls"||c.playback_type==="mp4")&&safe(c.stream_url)&&hasActivePlaybackRights(c)', webos)
        self.assertIn('p.setDisplayRect(0,0,window.innerWidth||1920,window.innerHeight||1080)', tizen)
        for source in (tizen, webos):
            self.assertIn('x.protocol==="https:"', source)
            self.assertIn("official", source.lower())
        self.assertNotIn("launchAppControl", tizen)
        self.assertNotIn('target="_blank"', webos)
        self.assertIn("no abrirá aplicaciones ni navegadores externos", tizen)
        self.assertIn("no abrirá navegadores externos", webos)

    def test_tv_navigation_and_free_policy_are_visible(self):
        for path in ("platforms/tizen/index.html", "platforms/webos/index.html"):
            source = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn("100 % gratis", source)
            self.assertIn("BACK", source)
        for path in ("platforms/tizen/app.js", "platforms/webos/app.js"):
            source = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn("e.keyCode===13", source)
            self.assertIn("if([37,38,39,40,35,36].includes(e.keyCode))", source)
            self.assertIn("move(e.keyCode)", source)


    def test_android_tv_uses_television_mode_and_free_branding(self):
        main = (ROOT / "app/src/main/java/com/matuucrea/miltvplus/MainActivity.kt").read_text(encoding="utf-8")
        ui = (ROOT / "app/src/main/java/com/matuucrea/miltvplus/ui/PremiumExperience.kt").read_text(encoding="utf-8")
        self.assertIn("UI_MODE_TYPE_TELEVISION", main)
        self.assertIn("amazon.hardware.fire_tv", main)
        self.assertIn("&& !isTelevision", main)
        self.assertIn('Text("MILTVPLUS Premium"', ui)
        self.assertIn("Experiencia completa · 100 % gratuita", ui)
        self.assertIn("Sin anuncios · Sin pagos · Sin suscripciones", ui)
        self.assertNotIn("billing", ui.lower())


    def test_official_smart_tv_shell_matches_reference_navigation_and_pages(self):
        shell = (ROOT / "platforms/shared/official-interface.js").read_text(encoding="utf-8")
        expected = ("Inicio", "TV en Vivo", "Películas", "Series", "Deportes", "Infantil", "Documentales", "Música", "Adultos 18+", "Favoritos", "Configuración", "Más Opciones")
        for label in expected:
            self.assertIn(label, shell)
        for platform in ("tizen", "webos"):
            script = (ROOT / f"platforms/{platform}/app.js").read_text(encoding="utf-8")
            css = (ROOT / f"platforms/{platform}/styles.css").read_text(encoding="utf-8")
            self.assertIn("mountOfficialInterface", script)
            self.assertIn("api/v2/catalog/", script)
            self.assertIn("api/v2/resolve/", script)
            self.assertIn("playCatalogItem", script)
            self.assertIn("official-sidebar", css)
            self.assertIn("adult-hero", css)
            self.assertIn("settings-layout", css)
        self.assertIn("Siempre en tu corazón", shell)
        self.assertIn("getCountryCounts", shell)
        self.assertIn("loadEpg", shell)
        self.assertIn("loadCatalog", shell)
        self.assertIn("renderVod", shell)
        self.assertIn("epg-timeline", shell)
        self.assertIn("epg-channel-info", shell)
        self.assertIn("optimizer.virtualizarLista(rows,root", shell)
        self.assertIn("optimizer.virtualizarLista(entries,grid", shell)
        self.assertIn("destruirLista", shell)
        self.assertIn("for(let i=0;i<7;i++)", shell)
        self.assertIn("official-count", shell)
        self.assertIn("localStorage.setItem", shell)
        self.assertIn("player-actions", shell)
        self.assertIn("favoriteButton", shell)
        self.assertIn("matchFavorite", shell)
        self.assertIn("row.hidden=row.dataset.group!==s", shell)
        self.assertIn('data-action="record" disabled', shell)
        self.assertNotIn('"AR","🇦🇷","Argentina","Siempre en tu corazón",194', shell)
        self.assertIn("No hay fuentes ni reproducciones activas", shell)

    def test_android_official_sidebar_and_country_phrases(self):
        ui = (ROOT / "app/src/main/java/com/matuucrea/miltvplus/ui/PremiumExperience.kt").read_text(encoding="utf-8")
        countries = (ROOT / "app/src/main/java/com/matuucrea/miltvplus/ui/AccountAndSettings.kt").read_text(encoding="utf-8")
        for label in ("Inicio", "TV en Vivo", "Películas", "Series", "Deportes", "Infantil", "Documentales", "Música", "Adultos 18+", "Favoritos", "Configuración"):
            self.assertIn(f'NavDestination("{label}"', ui)
        self.assertIn('Text("Más Opciones"', ui)
        self.assertIn("Argentina · Siempre en tu corazón", countries)
        self.assertIn("Canales publicados:", countries)
        self.assertIn("Restablecer Configuración", countries)

    def test_windows_official_sidebar_keeps_extra_destinations_under_more_options(self):
        xaml = (ROOT / "windows/MILTVPLUS.Desktop/MainWindow.xaml").read_text(encoding="utf-8")
        code = (ROOT / "windows/MILTVPLUS.Desktop/MainWindow.xaml.cs").read_text(encoding="utf-8")
        self.assertIn('Content="•••   Más Opciones"', xaml)
        self.assertIn('Header="Guía EPG" Click="MoreEpg_Click"', xaml)
        self.assertIn('Header="Países" Click="MoreCountries_Click"', xaml)
        self.assertIn("Argentina · Siempre en tu corazón", code)
        self.assertIn("MILTVPLUS Premium", xaml)
        self.assertIn("100 % gratuita", xaml)

    def test_fire_tv_compatibility_is_bounded_by_android_min_sdk(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        matrix = (ROOT / "docs/DEVICE-RELEASE-MATRIX.md").read_text(encoding="utf-8")
        self.assertIn("minSdk = 23", gradle)
        self.assertIn("Fire OS 5 (Android API 22) queda excluido", matrix)
        self.assertIn("Vega OS", matrix)
        self.assertIn("Fire OS 6 o posterior", matrix)

    def test_smart_tv_navigation_uses_grid_geometry_and_scrollable_catalog(self):
        for platform in ("tizen", "webos"):
            script = (ROOT / f"platforms/{platform}/app.js").read_text(encoding="utf-8")
            css = (ROOT / f"platforms/{platform}/styles.css").read_text(encoding="utf-8")
            self.assertIn("function gridColumns()", script)
            self.assertIn("selected+cols", script)
            self.assertIn("scrollTop", script)
            self.assertIn("grid-template-columns:repeat(auto-fill", css)
            self.assertIn("overflow-y:auto", css)

    def test_smart_tv_direct_playback_requires_current_regional_rights(self):
        for platform in ("tizen", "webos"):
            script = (ROOT / f"platforms/{platform}/app.js").read_text(encoding="utf-8")
            self.assertIn("rights_verified,rights_holder,rights_reference,rights_regions", script)
            self.assertIn("function hasActivePlaybackRights(c)", script)
            self.assertIn("regions.some(value=>String(value).toUpperCase()===region)", script)
            self.assertIn("rights_valid_until", script)
            self.assertIn("hasActivePlaybackRights(c)", script)

    def test_android_and_windows_gate_direct_streams_on_active_rights(self):
        android = (ROOT / "app/src/main/java/com/matuucrea/miltvplus/data/SupabaseCatalogRepository.kt").read_text(encoding="utf-8")
        windows = (ROOT / "windows/MILTVPLUS.Desktop/MainWindow.xaml.cs").read_text(encoding="utf-8")
        playback = (ROOT / "windows/MILTVPLUS.Desktop/OfficialPlayback.cs").read_text(encoding="utf-8")
        for field in ("rights_verified", "rights_holder", "rights_reference", "rights_regions", "rights_valid_from", "rights_valid_until"):
            self.assertIn(field, android)
            self.assertIn(field, windows)
        self.assertIn("hasActivePlaybackRights(row, table)", android)
        self.assertIn("Locale.getDefault().country", android)
        self.assertIn("item.HasActivePlaybackRights", windows)
        self.assertIn("entry.HasActivePlaybackRights", playback)
        self.assertIn("IsUserProvidedSource = true", windows)
        self.assertIn("val until = if (untilRaw == null) null else parseRightsDate(untilRaw) ?: return false", android)
        self.assertIn("entry.IsUserProvidedSource || (IsHttps(entry.StreamUrl) && entry.HasActivePlaybackRights)", playback)


    def test_android_and_windows_use_native_playback_engines(self):
        android = (ROOT / "app/src/main/java/com/matuucrea/miltvplus/player/ChannelPlayerActivity.kt").read_text(encoding="utf-8")
        gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        windows_project = (ROOT / "windows/MILTVPLUS.Desktop/MILTVPLUS.Desktop.csproj").read_text(encoding="utf-8")
        windows_main = (ROOT / "windows/MILTVPLUS.Desktop/MainWindow.xaml.cs").read_text(encoding="utf-8")
        windows_playback = (ROOT / "windows/MILTVPLUS.Desktop/OfficialPlayback.cs").read_text(encoding="utf-8")
        self.assertIn("ExoPlayer.Builder(this)", android)
        self.assertIn("MimeTypes.APPLICATION_M3U8", android)
        self.assertIn("MimeTypes.APPLICATION_MPD", android)
        self.assertIn("media3-exoplayer-hls", gradle)
        self.assertIn("media3-exoplayer-dash", gradle)
        self.assertIn("LibVLCSharp.WPF", windows_project)
        self.assertIn("new MediaPlayer(_libVlc)", windows_main)
        self.assertIn("_mediaPlayer.Play", windows_main)
        self.assertIn("ToggleFullscreen()", windows_playback)


    def test_shared_performance_optimizer_releases_resources_and_virtualizes(self):
        optimizer = (ROOT / "platforms/shared/performance-optimizer.js").read_text(encoding="utf-8")
        for token in ("export class PerformanceOptimizer", "preload = \"none\"", "pause?.()", "removeAttribute?.(\"src\")", "srcObject = null", "virtualizarLista", "ResizeObserver", "maxDomNodes", "destruirTodo"):
            self.assertIn(token, optimizer)
        self.assertNotIn("window.gc", optimizer)
        self.assertNotIn("globalThis.gc", optimizer)

    def test_smart_tv_clients_use_shared_resource_cleanup(self):
        for platform in ("tizen", "webos"):
            script = (ROOT / f"platforms/{platform}/app.js").read_text(encoding="utf-8")
            page = (ROOT / f"platforms/{platform}/index.html").read_text(encoding="utf-8")
            css = (ROOT / f"platforms/{platform}/styles.css").read_text(encoding="utf-8")
            self.assertIn('import { optimizer }', script)
            self.assertIn("optimizer.destruirTodo()", script)
            self.assertIn('type="module"', page)
            self.assertIn("content-visibility: auto", css)

    def test_all_native_tv_clients_are_marked_as_source_not_signed_packages(self):
        docs = (ROOT / "docs/NATIVE-TV-CLIENTS.md").read_text(encoding="utf-8")
        self.assertIn("WGT firmado", docs)
        self.assertIn("IPK firmado", docs)
        self.assertIn("SDK/certificado/QA pendientes", docs)
        self.assertIn("WGT/IPK no se generan", docs)


if __name__ == "__main__":
    unittest.main()
