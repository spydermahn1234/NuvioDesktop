namespace NuvioLights.Tests;

/// <summary>A light that remembers what it was told.</summary>
internal sealed class FakeLight : ILight
{
    public FakeLight(LightConfig config, int min = 1) { Config = config; MinBrightness = min; }
    public LightConfig Config { get; }
    public int MinBrightness { get; }
    public bool On { get; set; } = true;
    public int Brightness { get; set; } = 100;
    public bool Reachable { get; set; } = true;
    /// <summary>Where the light really is; null means wherever it is asked.</summary>
    public string? ActualIp { get; set; }
    public List<string> Calls { get; } = new();

    public Task<LightReading?> ReadAsync(CancellationToken ct) =>
        Task.FromResult(Reachable ? new LightReading(On, Brightness) : null);

    public Task SetBrightnessAsync(int percent, CancellationToken ct)
    {
        Brightness = Math.Clamp(percent, MinBrightness, 100);
        Calls.Add($"b{Brightness}");
        return Task.CompletedTask;
    }

    public Task SetPowerAsync(bool on, CancellationToken ct)
    {
        On = on;
        Calls.Add(on ? "on" : "off");
        return Task.CompletedTask;
    }
}

internal sealed class FakeFactory : ILightFactory
{
    public Dictionary<string, FakeLight> Lights { get; } = new();
    public List<FoundLight> Found { get; } = new();

    public FakeLight Add(LightConfig config, int min = 1) => Lights[config.Id] = new FakeLight(config, min);

    public ILight Create(LightConfig config)
    {
        var light = Lights[config.Id];
        light.Config.Ip = config.Ip;
        return new Proxy(light, config);
    }

    public Task<IReadOnlyList<FoundLight>> ScanAsync(CancellationToken ct) => Task.FromResult<IReadOnlyList<FoundLight>>(Found);

    /// <summary>Reads through to the shared fake, and only answers at the address the fake is at now.</summary>
    private sealed class Proxy(FakeLight inner, LightConfig config) : ILight
    {
        public LightConfig Config => config;
        public int MinBrightness => inner.MinBrightness;
        private bool Here => inner.Reachable && (inner.ActualIp == null || inner.ActualIp == config.Ip);
        public Task<LightReading?> ReadAsync(CancellationToken ct) => Here ? inner.ReadAsync(ct) : Task.FromResult<LightReading?>(null);
        public Task SetBrightnessAsync(int percent, CancellationToken ct) => inner.SetBrightnessAsync(percent, ct);
        public Task SetPowerAsync(bool on, CancellationToken ct) => inner.SetPowerAsync(on, ct);
    }
}
