using System.Text.Json;

namespace NuvioLights;

/// <summary>
/// Carries a mood out on the lights. Going down, it reads each light first and keeps that reading; coming back,
/// it returns each light to exactly that. A light that was off is left off, and a light someone switched off during
/// the film stays off. The readings are kept on disk too, so a restart (or a crash) while dimmed still restores.
/// A new mood cancels a fade that is still running.
/// </summary>
public sealed class LightRig
{
    private readonly Func<Settings> settings;
    private readonly ILightFactory factory;
    private readonly string? snapshotPath;
    private readonly SemaphoreSlim gate = new(1, 1);
    private CancellationTokenSource? running;
    private Dictionary<string, LightReading> saved = new();

    public LightRig(Func<Settings> settings, ILightFactory factory, string? snapshotPath)
    {
        this.settings = settings;
        this.factory = factory;
        this.snapshotPath = snapshotPath;
        saved = LoadSnapshot();
    }

    /// <summary>True when readings from an earlier dim are waiting to be restored.</summary>
    public bool HasSaved => saved.Count > 0;

    /// <summary>Raised when a light could not be reached, with its name.</summary>
    public event Action<string>? Unreachable;

    public async Task ApplyAsync(Mood mood, CancellationToken stop)
    {
        running?.Cancel();
        var mine = CancellationTokenSource.CreateLinkedTokenSource(stop);
        running = mine;
        await gate.WaitAsync(stop);
        try
        {
            var s = settings();
            var lights = s.Lights.Where(l => l.Action != LightAction.Ignore || saved.ContainsKey(Key(l))).ToList();
            var work = lights.Select(l => OneAsync(l, mood, s, mine.Token));
            await Task.WhenAll(work);
        }
        finally
        {
            SaveSnapshot();
            gate.Release();
        }
    }

    /// <summary>One light's part; a light that fails never stops the others.</summary>
    private async Task OneAsync(LightConfig l, Mood mood, Settings s, CancellationToken ct)
    {
        try
        {
            if (mood == Mood.Dimmed) await DimAsync(l, s, ct);
            else await RestoreAsync(l, s, ct);
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception e)
        {
            Log.Write($"{l.Name}: {e.Message}");
        }
    }

    private async Task DimAsync(LightConfig config, Settings s, CancellationToken ct)
    {
        if (config.Action == LightAction.Ignore) return;
        var light = factory.Create(config);
        var key = Key(config);
        bool resumed;
        lock (saved) resumed = saved.ContainsKey(key);
        var now = await ReadOrFindAsync(light, ct);
        if (now == null || !now.On) return; // Off before the film (or switched off since): leave it alone.
        if (!resumed)
        {
            // Keep the first reading only: after a restore that was cut short, the light is part way up, and the
            // room's real state is still the one read before the film.
            lock (saved) saved[key] = now;
            SaveSnapshot();
        }
        var target = config.Action == LightAction.Off
            ? light.MinBrightness
            : Math.Max(light.MinBrightness, Math.Clamp(config.Level, 1, 100));
        // Never brighten a light that was already lower than the dim level, unless it is coming back down from a
        // restore that was cut short.
        if (!resumed && now.Brightness < target) target = now.Brightness;
        if (now.Brightness != target) await FadeAsync(light, now.Brightness, target, s.FadeMs, ct);
        if (config.Action == LightAction.Off) await light.SetPowerAsync(false, ct);
    }

    private async Task RestoreAsync(LightConfig config, Settings s, CancellationToken ct)
    {
        var key = Key(config);
        LightReading? before;
        lock (saved) saved.TryGetValue(key, out before);
        if (before == null) return;
        var light = factory.Create(config);
        var now = await ReadOrFindAsync(light, ct);
        if (now == null) return; // Unreachable: keep the reading and try again next time.
        if (!now.On && config.Action != LightAction.Off)
        {
            // Someone switched it off during the film; that was on purpose.
            Forget(key);
            return;
        }
        var from = now.Brightness;
        if (!now.On)
        {
            await light.SetBrightnessAsync(light.MinBrightness, ct);
            await light.SetPowerAsync(true, ct);
            from = light.MinBrightness;
        }
        if (from != before.Brightness) await FadeAsync(light, from, before.Brightness, s.FadeMs, ct);
        Forget(key);
    }

    private void Forget(string key)
    {
        lock (saved) saved.Remove(key);
    }

    private async Task<LightReading?> ReadOrFindAsync(ILight light, CancellationToken ct)
    {
        var reading = await light.ReadAsync(ct);
        if (reading != null) return reading;
        // Its address may have changed (DHCP): look for it by its id and try once more.
        var found = (await factory.ScanAsync(ct)).FirstOrDefault(f => f.Kind == light.Config.Kind && f.Id == light.Config.Id);
        if (found != null && found.Ip != light.Config.Ip)
        {
            Log.Write($"{light.Config.Name} moved from {light.Config.Ip} to {found.Ip}");
            light.Config.Ip = found.Ip;
            reading = await light.ReadAsync(ct);
        }
        if (reading == null)
        {
            Log.Write($"{light.Config.Name} ({light.Config.Ip}) did not answer");
            Unreachable?.Invoke(light.Config.Name);
        }
        return reading;
    }

    /// <summary>Steps the brightness from one level to another over about <paramref name="ms"/>.</summary>
    internal static async Task FadeAsync(ILight light, int from, int to, int ms, CancellationToken ct)
    {
        to = Math.Max(light.MinBrightness, to);
        var steps = Math.Clamp(ms / 150, 1, 12);
        for (var i = 1; i <= steps; i++)
        {
            ct.ThrowIfCancellationRequested();
            var level = from + (to - from) * i / steps;
            await light.SetBrightnessAsync(level, ct);
            if (i < steps) await Task.Delay(ms / steps, ct);
        }
    }

    private static string Key(LightConfig l) => $"{l.Kind}:{l.Id}";

    private Dictionary<string, LightReading> LoadSnapshot()
    {
        try
        {
            // Readings from a crash long ago are stale: the room has been changed by hand since.
            if (snapshotPath != null && File.Exists(snapshotPath) && File.GetLastWriteTimeUtc(snapshotPath) > DateTime.UtcNow.AddHours(-12))
                return JsonSerializer.Deserialize<Dictionary<string, LightReading>>(File.ReadAllText(snapshotPath)) ?? new();
        }
        catch (Exception e) when (e is JsonException or IOException)
        {
        }
        return new();
    }

    private void SaveSnapshot()
    {
        if (snapshotPath == null) return;
        try
        {
            string json;
            lock (saved) json = JsonSerializer.Serialize(saved);
            Directory.CreateDirectory(Path.GetDirectoryName(snapshotPath)!);
            File.WriteAllText(snapshotPath, json);
        }
        catch (IOException)
        {
        }
    }
}
