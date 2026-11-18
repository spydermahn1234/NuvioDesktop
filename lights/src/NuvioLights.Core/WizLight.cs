using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace NuvioLights;

/// <summary>
/// A WiZ bulb over its local UDP API (port 38899, JSON): getPilot reads it, setPilot changes it. Only power and
/// brightness are touched, so the bulb keeps its colour, white temperature or scene.
/// </summary>
public sealed class WizLight : ILight
{
    public const int Port = 38899;

    public WizLight(LightConfig config) => Config = config;

    public LightConfig Config { get; }
    public int MinBrightness => 10;

    public async Task<LightReading?> ReadAsync(CancellationToken ct)
    {
        var reply = await SendAsync("""{"method":"getPilot","params":{}}""", ct);
        var result = reply?["result"];
        if (result == null) return null;
        var on = result["state"]?.GetValue<bool>() ?? false;
        var dimming = result["dimming"]?.GetValue<int>() ?? 100;
        return new LightReading(on, dimming);
    }

    public Task SetBrightnessAsync(int percent, CancellationToken ct) =>
        SetPilotAsync(new JsonObject { ["dimming"] = Math.Clamp(percent, MinBrightness, 100) }, ct);

    public Task SetPowerAsync(bool on, CancellationToken ct) => SetPilotAsync(new JsonObject { ["state"] = on }, ct);

    private async Task SetPilotAsync(JsonObject parameters, CancellationToken ct)
    {
        var message = new JsonObject { ["id"] = 1, ["method"] = "setPilot", ["params"] = parameters }.ToJsonString();
        if (await SendAsync(message, ct) == null) throw new IOException($"WiZ {Config.Name} ({Config.Ip}) did not answer");
    }

    private async Task<JsonNode?> SendAsync(string message, CancellationToken ct)
    {
        if (!IPAddress.TryParse(Config.Ip, out var ip)) return null;
        using var udp = new UdpClient(AddressFamily.InterNetwork);
        var bytes = Encoding.UTF8.GetBytes(message);
        for (var attempt = 0; attempt < 3; attempt++)
        {
            await udp.SendAsync(bytes, bytes.Length, new IPEndPoint(ip, Port));
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
            timeout.CancelAfter(700);
            try
            {
                var result = await udp.ReceiveAsync(timeout.Token);
                return JsonNode.Parse(result.Buffer);
            }
            catch (OperationCanceledException) when (!ct.IsCancellationRequested)
            {
            }
            catch (JsonException)
            {
            }
        }
        return null;
    }

    /// <summary>Asks every WiZ bulb on the network (broadcast getPilot), collecting answers for two seconds.</summary>
    public static async Task<IReadOnlyList<FoundLight>> ScanAsync(CancellationToken ct)
    {
        using var udp = new UdpClient(AddressFamily.InterNetwork) { EnableBroadcast = true };
        var request = Encoding.UTF8.GetBytes("""{"method":"getPilot","params":{}}""");
        foreach (var target in BroadcastAddresses())
        {
            try { await udp.SendAsync(request, request.Length, new IPEndPoint(target, Port)); }
            catch (SocketException) { }
        }
        var found = new Dictionary<string, FoundLight>();
        using var window = CancellationTokenSource.CreateLinkedTokenSource(ct);
        window.CancelAfter(2_000);
        try
        {
            while (true)
            {
                var result = await udp.ReceiveAsync(window.Token);
                try
                {
                    var mac = JsonNode.Parse(result.Buffer)?["result"]?["mac"]?.GetValue<string>();
                    if (string.IsNullOrEmpty(mac)) continue;
                    found[mac] = new FoundLight(LightKind.Wiz, mac, result.RemoteEndPoint.Address.ToString(), "WiZ");
                }
                catch (Exception e) when (e is JsonException or InvalidOperationException)
                {
                }
            }
        }
        catch (OperationCanceledException) when (!ct.IsCancellationRequested)
        {
        }
        return found.Values.ToList();
    }

    /// <summary>255.255.255.255 plus each home network's own broadcast address (more reliable on PCs with several adapters).</summary>
    internal static IEnumerable<IPAddress> BroadcastAddresses()
    {
        yield return IPAddress.Broadcast;
        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up || nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            foreach (var unicast in nic.GetIPProperties().UnicastAddresses)
            {
                if (unicast.Address.AddressFamily != AddressFamily.InterNetwork || unicast.IPv4Mask == null) continue;
                var ip = unicast.Address.GetAddressBytes();
                var mask = unicast.IPv4Mask.GetAddressBytes();
                var broadcast = new byte[4];
                for (var i = 0; i < 4; i++) broadcast[i] = (byte)(ip[i] | ~mask[i]);
                yield return new IPAddress(broadcast);
            }
        }
    }
}
