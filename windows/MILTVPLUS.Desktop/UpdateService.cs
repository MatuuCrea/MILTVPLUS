using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace MILTVPLUS.Desktop;

public sealed record UpdateRelease(Version Version, Uri InstallerUri, Uri ChecksumsUri, Uri ReleaseUri);

public static class UpdateService
{
    private const string LatestReleaseApi = "https://api.github.com/repos/MatuuCrea/MILTVPLUS/releases/latest";
    private const long MaxInstallerBytes = 250L * 1024 * 1024;
    private static readonly HttpClient Client = CreateClient();

    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromMinutes(5) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("MILTVPLUS-Windows-Updater");
        client.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        return client;
    }

    public static async Task<UpdateRelease?> CheckAsync(Version currentVersion, CancellationToken cancellationToken = default)
    {
        using var response = await Client.GetAsync(LatestReleaseApi, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NotFound) return null;
        response.EnsureSuccessStatusCode();
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken));
        var root = document.RootElement;
        if (root.TryGetProperty("draft", out var draft) && draft.GetBoolean()) return null;
        if (root.TryGetProperty("prerelease", out var prerelease) && prerelease.GetBoolean()) return null;

        var tag = root.GetProperty("tag_name").GetString()?.TrimStart('v', 'V');
        if (!Version.TryParse(tag, out var latest)) throw new InvalidDataException("La versión publicada no tiene formato numérico.");
        if (latest <= currentVersion) return null;

        Uri? installer = null;
        Uri? checksums = null;
        foreach (var asset in root.GetProperty("assets").EnumerateArray())
        {
            var name = asset.GetProperty("name").GetString();
            var address = asset.GetProperty("browser_download_url").GetString();
            if (!Uri.TryCreate(address, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
                continue;
            if (name == "MILTVPLUS-Setup-x64.exe") installer = uri;
            if (name == "SHA256SUMS") checksums = uri;
        }

        var releaseAddress = root.GetProperty("html_url").GetString();
        if (installer is null || checksums is null ||
            !Uri.TryCreate(releaseAddress, UriKind.Absolute, out var releaseUri) ||
            releaseUri.Scheme != Uri.UriSchemeHttps)
            throw new InvalidDataException("La publicación no incluye instalador, checksum o enlace HTTPS válido.");

        return new UpdateRelease(latest, installer, checksums, releaseUri);
    }

    public static async Task<string> DownloadAndVerifyAsync(UpdateRelease release, CancellationToken cancellationToken = default)
    {
        var checksumText = await Client.GetStringAsync(release.ChecksumsUri, cancellationToken);
        var expected = checksumText.Split('\n', StringSplitOptions.RemoveEmptyEntries)
            .Select(line => Regex.Match(line.Trim(), @"^([a-fA-F0-9]{64})\s+\*?MILTVPLUS-Setup-x64\.exe$"))
            .FirstOrDefault(match => match.Success)?.Groups[1].Value;
        if (string.IsNullOrWhiteSpace(expected))
            throw new InvalidDataException("SHA256SUMS no contiene el hash del instalador de Windows.");

        var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MILTVPLUS", "Updates");
        Directory.CreateDirectory(directory);
        var finalPath = Path.Combine(directory, $"MILTVPLUS-Setup-{release.Version}-x64.exe");
        var temporaryPath = finalPath + ".part";

        using var response = await Client.GetAsync(release.InstallerUri, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();
        if (response.RequestMessage?.RequestUri?.Scheme != Uri.UriSchemeHttps)
            throw new InvalidDataException("La descarga intentó salir de HTTPS.");
        if (response.Content.Headers.ContentLength is long length && length > MaxInstallerBytes)
            throw new InvalidDataException("El instalador supera el tamaño máximo permitido.");

        using var input = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var output = new FileStream(temporaryPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true);
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        var buffer = new byte[81920];
        long total = 0;
        try
        {
            while (true)
            {
                var count = await input.ReadAsync(buffer.AsMemory(), cancellationToken);
                if (count == 0) break;
                total += count;
                if (total > MaxInstallerBytes) throw new InvalidDataException("El instalador supera el tamaño máximo permitido.");
                hash.AppendData(buffer, 0, count);
                await output.WriteAsync(buffer.AsMemory(0, count), cancellationToken);
            }
            await output.FlushAsync(cancellationToken);
            var actual = Convert.ToHexString(hash.GetHashAndReset());
            if (!actual.Equals(expected, StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("La verificación SHA-256 falló. El instalador no se ejecutará.");
        }
        catch
        {
            output.Dispose();
            if (File.Exists(temporaryPath)) File.Delete(temporaryPath);
            throw;
        }
        await output.DisposeAsync();
        File.Move(temporaryPath, finalPath, overwrite: true);
        return finalPath;
    }

    public static void LaunchInstaller(string installerPath)
    {
        if (!File.Exists(installerPath)) throw new FileNotFoundException("No se encontró el instalador verificado.", installerPath);
        Process.Start(new ProcessStartInfo(installerPath) { UseShellExecute = true });
    }
}
