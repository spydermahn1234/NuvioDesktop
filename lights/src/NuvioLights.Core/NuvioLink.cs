using System.Net;
using System.Net.Sockets;
using System.Text;

namespace NuvioLights;

public enum LinkStatus { Searching, NeedsPairing, Connected, Locked, Unsupported }

/// <summary>
/// Connects to Nuvio the way Nuvio Remote does (Settings › Keyboard shortcuts › Phone remote must be on):
/// finds the port with the UDP discovery on 47320, pairs once with the six-digit code, then sends SUBSCRIBE
/// and reads STATE / IDLE lines. Reconnects by itself when Nuvio restarts.
/// </summary>
public sealed class NuvioLink
{
    private const string DeviceName = "Nuvio Lights";
    private const int PingEveryMs = 8_000; // Nuvio drops a client that is silent for 20 s.
    private const int ReplyTimeoutMs = 10_000;

    private readonly Func<string> host;
    private readonly Func<string?> token;
    private SemaphoreSlim pairRequest = new(0);
    private string? pin;

    public NuvioLink(Func<string> host, Func<string?> token)
    {
        this.host = host;
        this.token = token;
    }

    /// <summary>Playback from Nuvio; null for IDLE or when the connection drops.</summary>
    public event Action<Playback?>? PlaybackChanged;
    public event Action<LinkStatus, string>? StatusChanged;
    /// <summary>Nuvio accepted the code: keep the token (and the PC's name) for next time.</summary>
    public event Action<string, string>? Paired;

    /// <summary>Asked when Nuvio wants the pairing code; return null to stop asking until <see cref="PairAgain"/>.</summary>
    public Func<string, Task<string?>>? AskPin { get; set; }

    public LinkStatus Status { get; private set; } = LinkStatus.Searching;

    /// <summary>The tray's "Pair with Nuvio…": ask for the code again.</summary>
    public void PairAgain() => pairRequest.Release();

    /// <summary>Drops the current connection so the next one uses new settings (a changed host).</summary>
    public void Reconnect() => current?.Close();

    private TcpClient? current;

    public async Task RunAsync(CancellationToken stop)
    {
        string? lastReason = null;
        while (!stop.IsCancellationRequested)
        {
            try
            {
                var reason = await SessionAsync(stop);
                if (reason != null && reason != lastReason) Log.Write($"Nuvio: {reason}");
                lastReason = reason;
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested)
            {
                break;
            }
            catch (Exception e) when (e is IOException or SocketException or ObjectDisposedException or InvalidOperationException)
            {
                if (lastReason != e.Message) Log.Write($"Nuvio connection: {e.Message}");
                lastReason = e.Message;
            }
            PlaybackChanged?.Invoke(null);
            try { await Task.Delay(3_000, stop); } catch (OperationCanceledException) { break; }
        }
    }

    /// <summary>One connection, start to end. Returns why it ended (for the log), or null.</summary>
    private async Task<string?> SessionAsync(CancellationToken stop)
    {
        var address = host();
        using var client = await ConnectAsync(address, stop);
        if (client == null)
        {
            SetStatus(LinkStatus.Searching, "Looking for Nuvio (is it running, with Phone remote on?)");
            return $"not found on {address}";
        }
        current = client;
        client.NoDelay = true;
        var stream = client.GetStream();
        var reader = new StreamReader(stream, new UTF8Encoding(false));
        var writer = new StreamWriter(stream, new UTF8Encoding(false)) { NewLine = "\n", AutoFlush = true };

        await writer.WriteLineAsync(NuvioLines.Hello(token(), DeviceName));
        var reply = await ReadAsync(reader, ReplyTimeoutMs, stop);
        if (reply == null) return "no answer to HELLO";
        var parts = reply.Split(' ');
        switch (parts[0])
        {
            case "WELCOME":
                break;
            case "PIN_REQUIRED":
                if (pin == null)
                {
                    // Nuvio gives the whole handshake 30 seconds, so ask first and come back with the code.
                    client.Close();
                    pin = await AskForPinAsync(stop);
                    return "pairing code needed";
                }
                await writer.WriteLineAsync($"PAIR {pin}");
                pin = null;
                var answer = await ReadAsync(reader, ReplyTimeoutMs, stop) ?? "";
                var a = answer.Split(' ');
                if (a[0] == "PAIRED" && a.Length >= 2)
                {
                    var name = a.Length > 2 ? NuvioLines.Decode(a[2]) : "Nuvio";
                    Log.Write($"Paired with {name}");
                    Paired?.Invoke(a[1], name);
                    parts = new[] { "WELCOME", a.Length > 2 ? a[2] : "Nuvio" };
                    break;
                }
                if (a[0] == "LOCKED")
                {
                    SetStatus(LinkStatus.Locked, "Too many wrong codes; Nuvio is waiting a minute");
                    await Task.Delay(60_000, stop);
                    return "locked";
                }
                SetStatus(LinkStatus.NeedsPairing, "That code was wrong");
                client.Close();
                pin = await AskForPinAsync(stop, wrong: true);
                return "wrong code";
            case "UNSUPPORTED":
                SetStatus(LinkStatus.Unsupported, "This Nuvio speaks a different remote version");
                await Task.Delay(60_000, stop);
                return "unsupported";
            default:
                return $"unexpected reply: {reply}";
        }

        var pcName = parts.Length > 1 ? NuvioLines.Decode(parts[1]) : "Nuvio";
        await writer.WriteLineAsync("SUBSCRIBE");
        SetStatus(LinkStatus.Connected, $"Connected to {pcName}");
        Log.Write($"Connected to {pcName}");

        using var sessionStop = CancellationTokenSource.CreateLinkedTokenSource(stop);
        var pinger = PingAsync(writer, sessionStop.Token);
        try
        {
            while (!stop.IsCancellationRequested)
            {
                var line = await reader.ReadLineAsync(stop);
                if (line == null) break;
                if (NuvioLines.TryParseState(line, out var playback)) PlaybackChanged?.Invoke(playback);
            }
        }
        finally
        {
            sessionStop.Cancel();
            try { await pinger; } catch (Exception) { }
            current = null;
        }
        SetStatus(LinkStatus.Searching, "Nuvio closed; waiting for it");
        return "disconnected";
    }

    private async Task<string?> AskForPinAsync(CancellationToken stop, bool wrong = false)
    {
        SetStatus(LinkStatus.NeedsPairing, wrong ? "That code was wrong" : "Pair with Nuvio to start");
        while (!stop.IsCancellationRequested)
        {
            var asked = AskPin == null ? null : await AskPin(wrong ? "That code didn't work. Try again:" : "");
            var code = asked?.Trim().Replace(" ", "");
            if (!string.IsNullOrEmpty(code)) return code;
            // Cancelled: wait for the tray's "Pair with Nuvio…" before asking again.
            await pairRequest.WaitAsync(stop);
            wrong = false;
        }
        return null;
    }

    private static async Task PingAsync(StreamWriter writer, CancellationToken stop)
    {
        while (!stop.IsCancellationRequested)
        {
            await Task.Delay(PingEveryMs, stop);
            await writer.WriteLineAsync("PING");
        }
    }

    private static async Task<string?> ReadAsync(StreamReader reader, int timeoutMs, CancellationToken stop)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(stop);
        timeout.CancelAfter(timeoutMs);
        try { return (await reader.ReadLineAsync(timeout.Token))?.Trim(); }
        catch (OperationCanceledException) when (!stop.IsCancellationRequested) { return null; }
    }

    private static async Task<TcpClient?> ConnectAsync(string address, CancellationToken stop)
    {
        var ports = new List<int>();
        var found = await DiscoverPortAsync(address, stop);
        if (found is { } p) ports.Add(p);
        for (var i = 0; i < NuvioLines.PortAttempts; i++)
        {
            var candidate = NuvioLines.DefaultPort + i;
            if (!ports.Contains(candidate)) ports.Add(candidate);
        }
        foreach (var port in ports)
        {
            var client = new TcpClient();
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(stop);
            timeout.CancelAfter(1_500);
            try
            {
                await client.ConnectAsync(address, port, timeout.Token);
                return client;
            }
            catch (Exception e) when (e is SocketException or OperationCanceledException && !stop.IsCancellationRequested)
            {
                client.Dispose();
            }
        }
        return null;
    }

    /// <summary>Nuvio answers <c>NUVIO_REMOTE_DISCOVER 1</c> on UDP 47320 with its TCP port.</summary>
    private static async Task<int?> DiscoverPortAsync(string address, CancellationToken stop)
    {
        try
        {
            var target = IPAddress.TryParse(address, out var ip) ? ip : (await Dns.GetHostAddressesAsync(address, stop))
                .FirstOrDefault(x => x.AddressFamily == AddressFamily.InterNetwork);
            if (target == null) return null;
            using var udp = new UdpClient(AddressFamily.InterNetwork);
            var request = Encoding.UTF8.GetBytes(NuvioLines.DiscoverRequest);
            await udp.SendAsync(request, request.Length, new IPEndPoint(target, NuvioLines.DiscoveryPort));
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(stop);
            timeout.CancelAfter(1_200);
            var result = await udp.ReceiveAsync(timeout.Token);
            return NuvioLines.TryParseDiscoveryReply(Encoding.UTF8.GetString(result.Buffer), out var port, out _) ? port : null;
        }
        catch (Exception e) when (e is SocketException or OperationCanceledException && !stop.IsCancellationRequested)
        {
            return null;
        }
    }

    private void SetStatus(LinkStatus status, string text)
    {
        Status = status;
        StatusChanged?.Invoke(status, text);
    }
}
