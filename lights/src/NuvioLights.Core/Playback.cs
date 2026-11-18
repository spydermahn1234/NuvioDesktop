namespace NuvioLights;

/// <summary>What Nuvio is playing, from its phone-remote STATE line. Null playback means IDLE.</summary>
public sealed record Playback(string Kind, bool Playing, string Title)
{
    public bool IsMusic => Kind.Equals("music", StringComparison.OrdinalIgnoreCase);
}

/// <summary>
/// Parses the lines Nuvio's phone-remote server sends (see PhoneRemoteProtocol.kt in the desktop app).
/// Only the lines Nuvio Lights needs are understood; everything else is ignored.
/// </summary>
public static class NuvioLines
{
    public const int Version = 1;
    public const int DefaultPort = 47321;
    public const int PortAttempts = 10;
    public const int DiscoveryPort = 47320;
    public const string DiscoverRequest = "NUVIO_REMOTE_DISCOVER 1";

    public static string Encode(string text) => Uri.EscapeDataString(text);

    public static string Decode(string text)
    {
        try { return Uri.UnescapeDataString(text.Replace("+", "%20")); }
        catch (UriFormatException) { return text; }
    }

    public static string Hello(string? token, string deviceName) =>
        $"HELLO {Version} {(string.IsNullOrWhiteSpace(token) ? "-" : token)} {Encode(deviceName)}";

    /// <summary>
    /// <c>STATE &lt;kind&gt; &lt;playing 0|1&gt; &lt;positionMs&gt; &lt;durationMs&gt; &lt;volume&gt; &lt;volumeMax&gt; &lt;canSkip&gt; &lt;title&gt; ...</c>
    /// or <c>IDLE</c>. Returns false for any other line; <paramref name="playback"/> is null for IDLE.
    /// </summary>
    public static bool TryParseState(string line, out Playback? playback)
    {
        playback = null;
        var parts = line.Trim().Split(' ');
        if (parts.Length == 0) return false;
        if (parts[0] == "IDLE") return true;
        if (parts[0] != "STATE" || parts.Length < 3) return false;
        var title = parts.Length > 8 && parts[8] != "-" ? Decode(parts[8]) : "";
        playback = new Playback(parts[1], parts[2] == "1", title);
        return true;
    }

    /// <summary><c>NUVIO_REMOTE 1 &lt;tcpPort&gt; &lt;name&gt;</c>, the answer to a discovery broadcast.</summary>
    public static bool TryParseDiscoveryReply(string line, out int port, out string name)
    {
        port = 0;
        name = "";
        var parts = line.Trim().Split(' ');
        if (parts.Length < 3 || parts[0] != "NUVIO_REMOTE" || !int.TryParse(parts[2], out port)) return false;
        name = parts.Length > 3 ? Decode(parts[3]) : "Nuvio";
        return true;
    }
}
