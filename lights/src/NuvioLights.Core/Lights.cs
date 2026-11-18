namespace NuvioLights;

/// <summary>A light's power and brightness (percent) as read before dimming.</summary>
public sealed record LightReading(bool On, int Brightness);

/// <summary>A light found on the network by a scan.</summary>
public sealed record FoundLight(LightKind Kind, string Id, string Ip, string Model);

public interface ILight
{
    LightConfig Config { get; }
    /// <summary>Lowest brightness the light takes (WiZ bulbs stop at 10 percent).</summary>
    int MinBrightness { get; }
    Task<LightReading?> ReadAsync(CancellationToken ct);
    Task SetBrightnessAsync(int percent, CancellationToken ct);
    Task SetPowerAsync(bool on, CancellationToken ct);
}

public interface ILightFactory
{
    ILight Create(LightConfig config);
    Task<IReadOnlyList<FoundLight>> ScanAsync(CancellationToken ct);
}

/// <summary>The real WiZ and Govee lights on the local network.</summary>
public sealed class LanLightFactory : ILightFactory
{
    public ILight Create(LightConfig config) => config.Kind switch
    {
        LightKind.Wiz => new WizLight(config),
        LightKind.Govee => new GoveeLight(config),
        _ => throw new ArgumentOutOfRangeException(nameof(config)),
    };

    public async Task<IReadOnlyList<FoundLight>> ScanAsync(CancellationToken ct)
    {
        var wiz = WizLight.ScanAsync(ct);
        var govee = GoveeLan.Shared.ScanAsync(ct);
        var all = new List<FoundLight>();
        try { all.AddRange(await wiz); } catch (Exception e) when (e is not OperationCanceledException) { Log.Write($"WiZ scan: {e.Message}"); }
        try { all.AddRange(await govee); } catch (Exception e) when (e is not OperationCanceledException) { Log.Write($"Govee scan: {e.Message}"); }
        return all;
    }
}
