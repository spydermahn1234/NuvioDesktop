using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json.Nodes;

namespace NuvioLights.Tests;

/// <summary>Talks to pretend devices on this machine's loopback, over the real wire formats.</summary>
[Collection("network")]
public class NetworkTests
{
    [Fact]
    public async Task WizReadsAndSetsBrightness()
    {
        using var bulb = new FakeWiz(dimming: 64);
        var light = new WizLight(new LightConfig { Kind = LightKind.Wiz, Ip = "127.0.0.1", Name = "Ceiling" });
        var reading = await light.ReadAsync(default);
        Assert.Equal(new LightReading(true, 64), reading);
        await light.SetBrightnessAsync(3, default);  // WiZ floor is 10
        Assert.Equal(10, bulb.Dimming);
        await light.SetPowerAsync(false, default);
        Assert.False(bulb.State);
    }

    [Fact]
    public async Task GoveeReadsStatusSetsBrightnessAndScans()
    {
        using var lan = new GoveeLan(listenPort: 14002, scanPort: 14001, controlPort: 14003, scanGroup: "127.0.0.1");
        using var strip = new FakeGovee(controlPort: 14003, scanPort: 14001, replyPort: 14002, brightness: 80);
        var light = new GoveeLight(new LightConfig { Kind = LightKind.Govee, Ip = "127.0.0.1", Name = "Strip" }, lan);
        Assert.Equal(new LightReading(true, 80), await light.ReadAsync(default));
        await light.SetBrightnessAsync(10, default);
        await WaitFor(() => strip.Brightness == 10);
        await light.SetPowerAsync(false, default);
        await WaitFor(() => !strip.On);
        var found = await lan.ScanAsync(default);
        Assert.Contains(found, f => f.Kind == LightKind.Govee && f.Id == "AA:BB:CC" && f.Model == "H6163");
    }

    [Fact]
    public async Task PairsWithNuvioThenDimsOnPlayAndRestoresOnPause()
    {
        using var nuvio = new FakeNuvio(pin: "123456");
        using var bulb = new FakeWiz(dimming: 70);
        var dir = Directory.CreateTempSubdirectory("nuviolights").FullName;
        var path = Path.Combine(dir, "settings.json");
        new Settings
        {
            FadeMs = 0, DimDelayMs = 100, PauseRestoreDelayMs = 50,
            Lights = { new LightConfig { Kind = LightKind.Wiz, Id = "a8bb50000001", Ip = "127.0.0.1", Name = "Ceiling", Level = 10 } },
        }.Save(path);
        var engine = new Engine(path);
        var asked = 0;
        engine.Link.AskPin = _ => { asked++; return Task.FromResult<string?>("123456"); };
        engine.Start();
        await WaitFor(() => engine.Link.Status == LinkStatus.Connected, 15_000);
        Assert.Equal(1, asked);
        Assert.Equal(nuvio.IssuedToken, engine.Settings.NuvioToken);

        nuvio.Send("STATE film 1 0 7200000 - 100 0 Dune - -");
        await WaitFor(() => bulb.Dimming == 10);
        nuvio.Send("STATE film 0 1000 7200000 - 100 0 Dune - -");
        await WaitFor(() => bulb.Dimming == 70);

        // Nuvio restarts: the saved token gets straight back in, no code asked.
        nuvio.DropClients();
        await WaitFor(() => nuvio.Welcomed > 0, 15_000);
        Assert.Equal(1, asked);
        await engine.ShutdownAsync();
    }

    internal static async Task WaitFor(Func<bool> condition, int ms = 5_000)
    {
        var until = DateTime.UtcNow.AddMilliseconds(ms);
        while (!condition())
        {
            if (DateTime.UtcNow > until) throw new TimeoutException("condition not met");
            await Task.Delay(25);
        }
    }
}

internal sealed class FakeWiz : IDisposable
{
    private readonly UdpClient udp = new(new IPEndPoint(IPAddress.Loopback, WizLight.Port));
    public int Dimming { get; private set; }
    public bool State { get; private set; } = true;

    public FakeWiz(int dimming)
    {
        Dimming = dimming;
        _ = Task.Run(Loop);
    }

    private async Task Loop()
    {
        while (true)
        {
            UdpReceiveResult r;
            try { r = await udp.ReceiveAsync(); } catch (ObjectDisposedException) { return; } catch (SocketException) { return; }
            var msg = JsonNode.Parse(r.Buffer)!;
            JsonNode reply;
            if (msg["method"]!.GetValue<string>() == "getPilot")
                reply = new JsonObject { ["method"] = "getPilot", ["result"] = new JsonObject { ["mac"] = "a8bb50000001", ["state"] = State, ["dimming"] = Dimming, ["temp"] = 2700 } };
            else
            {
                var p = msg["params"]!;
                if (p["dimming"] != null) Dimming = p["dimming"]!.GetValue<int>();
                if (p["state"] != null) State = p["state"]!.GetValue<bool>();
                reply = new JsonObject { ["method"] = "setPilot", ["result"] = new JsonObject { ["success"] = true } };
            }
            var bytes = Encoding.UTF8.GetBytes(reply.ToJsonString());
            await udp.SendAsync(bytes, bytes.Length, r.RemoteEndPoint);
        }
    }

    public void Dispose() => udp.Dispose();
}

internal sealed class FakeGovee : IDisposable
{
    private readonly UdpClient control;
    private readonly UdpClient scan;
    private readonly int replyPort;
    public int Brightness { get; private set; }
    public bool On { get; private set; } = true;

    public FakeGovee(int controlPort, int scanPort, int replyPort, int brightness)
    {
        Brightness = brightness;
        this.replyPort = replyPort;
        control = new UdpClient(new IPEndPoint(IPAddress.Loopback, controlPort));
        scan = new UdpClient(new IPEndPoint(IPAddress.Loopback, scanPort));
        _ = Task.Run(() => Loop(control));
        _ = Task.Run(() => Loop(scan));
    }

    private async Task Loop(UdpClient udp)
    {
        while (true)
        {
            UdpReceiveResult r;
            try { r = await udp.ReceiveAsync(); } catch (ObjectDisposedException) { return; } catch (SocketException) { return; }
            var msg = JsonNode.Parse(r.Buffer)!["msg"]!;
            var data = msg["data"];
            JsonNode? reply = null;
            switch (msg["cmd"]!.GetValue<string>())
            {
                case "scan":
                    reply = new JsonObject { ["msg"] = new JsonObject { ["cmd"] = "scan", ["data"] = new JsonObject { ["ip"] = "127.0.0.1", ["device"] = "AA:BB:CC", ["sku"] = "H6163" } } };
                    break;
                case "devStatus":
                    reply = new JsonObject { ["msg"] = new JsonObject { ["cmd"] = "devStatus", ["data"] = new JsonObject { ["onOff"] = On ? 1 : 0, ["brightness"] = Brightness } } };
                    break;
                case "brightness":
                    Brightness = data!["value"]!.GetValue<int>();
                    break;
                case "turn":
                    On = data!["value"]!.GetValue<int>() == 1;
                    break;
            }
            if (reply == null) continue;
            var bytes = Encoding.UTF8.GetBytes(reply.ToJsonString());
            using var sender = new UdpClient();
            await sender.SendAsync(bytes, bytes.Length, new IPEndPoint(r.RemoteEndPoint.Address, replyPort));
        }
    }

    public void Dispose()
    {
        control.Dispose();
        scan.Dispose();
    }
}

/// <summary>Nuvio's phone-remote server, as far as Nuvio Lights uses it.</summary>
internal sealed class FakeNuvio : IDisposable
{
    private readonly TcpListener tcp = new(IPAddress.Loopback, NuvioLines.DefaultPort);
    private readonly UdpClient discovery = new(new IPEndPoint(IPAddress.Loopback, NuvioLines.DiscoveryPort));
    private readonly string pin;
    private readonly List<(TcpClient client, StreamWriter writer)> clients = new();
    public string IssuedToken { get; } = "tok" + Guid.NewGuid().ToString("N")[..8];
    public int Welcomed;

    public FakeNuvio(string pin)
    {
        this.pin = pin;
        tcp.Start();
        _ = Task.Run(Accept);
        _ = Task.Run(Discover);
    }

    private async Task Discover()
    {
        while (true)
        {
            UdpReceiveResult r;
            try { r = await discovery.ReceiveAsync(); } catch (Exception) { return; }
            var reply = Encoding.UTF8.GetBytes($"NUVIO_REMOTE 1 {NuvioLines.DefaultPort} Test%20PC");
            await discovery.SendAsync(reply, reply.Length, r.RemoteEndPoint);
        }
    }

    private async Task Accept()
    {
        while (true)
        {
            TcpClient c;
            try { c = await tcp.AcceptTcpClientAsync(); } catch (Exception) { return; }
            _ = Task.Run(() => Serve(c));
        }
    }

    private async Task Serve(TcpClient c)
    {
        try
        {
            var reader = new StreamReader(c.GetStream());
            var writer = new StreamWriter(c.GetStream()) { AutoFlush = true, NewLine = "\n" };
            var hello = (await reader.ReadLineAsync())!.Split(' ');
            if (hello[2] == IssuedToken)
            {
                Interlocked.Increment(ref Welcomed);
                await writer.WriteLineAsync("WELCOME Test%20PC");
            }
            else
            {
                await writer.WriteLineAsync("PIN_REQUIRED");
                var pair = await reader.ReadLineAsync();
                if (pair == null) return;
                if (pair != $"PAIR {pin}") { await writer.WriteLineAsync("BAD_PIN"); return; }
                await writer.WriteLineAsync($"PAIRED {IssuedToken} Test%20PC");
            }
            if (await reader.ReadLineAsync() != "SUBSCRIBE") return;
            lock (clients) clients.Add((c, writer));
            await writer.WriteLineAsync("IDLE");
            while (await reader.ReadLineAsync() is { } line)
                if (line == "PING") await writer.WriteLineAsync("PONG");
        }
        catch (Exception)
        {
        }
    }

    public void Send(string line)
    {
        lock (clients) foreach (var (_, w) in clients) w.WriteLine(line);
    }

    public void DropClients()
    {
        lock (clients)
        {
            foreach (var (c, _) in clients) c.Close();
            clients.Clear();
        }
    }

    public void Dispose()
    {
        DropClients();
        tcp.Stop();
        discovery.Dispose();
    }
}
