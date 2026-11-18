using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace NuvioLights;

/// <summary>
/// Govee's LAN API (turn on "LAN Control" for each light in the Govee Home app). Commands go to the light on UDP 4003;
/// the light answers scans and devStatus on UDP 4002 of this PC, so one shared listener sorts the answers by sender.
/// A scan is a multicast to 239.255.255.250:4001.
/// </summary>
public sealed class GoveeLan : IDisposable
{
    public static readonly GoveeLan Shared = new();

    private readonly int listenPort;
    private readonly int scanPort;
    private readonly int controlPort;
    private readonly IPAddress scanGroup;
    private readonly object gate = new();
    private UdpClient? listener;
    private readonly ConcurrentDictionary<string, TaskCompletionSource<JsonNode>> statusWaiters = new();
    private Action<IPEndPoint, JsonNode>? scanSink;

    public GoveeLan(int listenPort = 4002, int scanPort = 4001, int controlPort = 4003, string scanGroup = "239.255.255.250")
    {
        this.listenPort = listenPort;
        this.scanPort = scanPort;
        this.controlPort = controlPort;
        this.scanGroup = IPAddress.Parse(scanGroup);
    }

    public int ControlPort => controlPort;

    private void EnsureListening()
    {
        lock (gate)
        {
            if (listener != null) return;
            var udp = new UdpClient(AddressFamily.InterNetwork);
            udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            udp.Client.Bind(new IPEndPoint(IPAddress.Any, listenPort));
            listener = udp;
            _ = Task.Run(() => ListenAsync(udp));
        }
    }

    private async Task ListenAsync(UdpClient udp)
    {
        while (true)
        {
            UdpReceiveResult result;
            try { result = await udp.ReceiveAsync(); }
            catch (ObjectDisposedException) { return; }
            catch (SocketException) { continue; }
            JsonNode? msg;
            try { msg = JsonNode.Parse(result.Buffer)?["msg"]; }
            catch (JsonException) { continue; }
            var cmd = msg?["cmd"]?.GetValue<string>();
            var data = msg?["data"];
            if (data == null) continue;
            if (cmd == "devStatus" && statusWaiters.TryRemove(result.RemoteEndPoint.Address.ToString(), out var waiter))
                waiter.TrySetResult(data);
            else if (cmd == "scan")
                scanSink?.Invoke(result.RemoteEndPoint, data);
        }
    }

    public async Task SendAsync(string ip, string cmd, JsonNode data, CancellationToken ct)
    {
        var message = new JsonObject { ["msg"] = new JsonObject { ["cmd"] = cmd, ["data"] = data } }.ToJsonString();
        var bytes = Encoding.UTF8.GetBytes(message);
        using var udp = new UdpClient(AddressFamily.InterNetwork);
        await udp.SendAsync(bytes, bytes.Length, new IPEndPoint(IPAddress.Parse(ip), controlPort));
    }

    public async Task<JsonNode?> StatusAsync(string ip, CancellationToken ct)
    {
        EnsureListening();
        for (var attempt = 0; attempt < 3; attempt++)
        {
            var waiter = new TaskCompletionSource<JsonNode>(TaskCreationOptions.RunContinuationsAsynchronously);
            statusWaiters[ip] = waiter;
            await SendAsync(ip, "devStatus", new JsonObject(), ct);
            var done = await Task.WhenAny(waiter.Task, Task.Delay(800, ct));
            if (done == waiter.Task) return waiter.Task.Result;
        }
        statusWaiters.TryRemove(ip, out _);
        return null;
    }

    public async Task<IReadOnlyList<FoundLight>> ScanAsync(CancellationToken ct)
    {
        EnsureListening();
        var found = new ConcurrentDictionary<string, FoundLight>();
        scanSink = (from, data) =>
        {
            var id = data["device"]?.GetValue<string>() ?? from.Address.ToString();
            var ip = data["ip"]?.GetValue<string>() ?? from.Address.ToString();
            found[id] = new FoundLight(LightKind.Govee, id, ip, data["sku"]?.GetValue<string>() ?? "Govee");
        };
        try
        {
            var bytes = Encoding.UTF8.GetBytes("""{"msg":{"cmd":"scan","data":{"account_topic":"reserve"}}}""");
            using var udp = new UdpClient(AddressFamily.InterNetwork);
            udp.Client.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.MulticastTimeToLive, 2);
            for (var i = 0; i < 2; i++)
            {
                await udp.SendAsync(bytes, bytes.Length, new IPEndPoint(scanGroup, scanPort));
                await Task.Delay(1_000, ct);
            }
            await Task.Delay(1_000, ct);
        }
        finally
        {
            scanSink = null;
        }
        return found.Values.ToList();
    }

    public void Dispose()
    {
        lock (gate)
        {
            listener?.Dispose();
            listener = null;
        }
    }
}

public sealed class GoveeLight : ILight
{
    private readonly GoveeLan lan;

    public GoveeLight(LightConfig config, GoveeLan? lan = null)
    {
        Config = config;
        this.lan = lan ?? GoveeLan.Shared;
    }

    public LightConfig Config { get; }
    public int MinBrightness => 1;

    public async Task<LightReading?> ReadAsync(CancellationToken ct)
    {
        var data = await lan.StatusAsync(Config.Ip, ct);
        if (data == null) return null;
        var on = (data["onOff"]?.GetValue<int>() ?? 0) == 1;
        var brightness = data["brightness"]?.GetValue<int>() ?? 100;
        return new LightReading(on, brightness);
    }

    public Task SetBrightnessAsync(int percent, CancellationToken ct) =>
        lan.SendAsync(Config.Ip, "brightness", new JsonObject { ["value"] = Math.Clamp(percent, MinBrightness, 100) }, ct);

    public Task SetPowerAsync(bool on, CancellationToken ct) =>
        lan.SendAsync(Config.Ip, "turn", new JsonObject { ["value"] = on ? 1 : 0 }, ct);
}
