namespace NuvioLights;

public static class LightTools
{
    /// <summary>Blinks a light twice so you can tell which one it is, then puts it back as it was.</summary>
    public static async Task<bool> FlashAsync(ILight light, CancellationToken ct)
    {
        var before = await light.ReadAsync(ct);
        if (before == null) return false;
        if (!before.On) await light.SetPowerAsync(true, ct);
        for (var i = 0; i < 2; i++)
        {
            await light.SetBrightnessAsync(light.MinBrightness, ct);
            await Task.Delay(450, ct);
            await light.SetBrightnessAsync(100, ct);
            await Task.Delay(450, ct);
        }
        await light.SetBrightnessAsync(before.Brightness, ct);
        if (!before.On) await light.SetPowerAsync(false, ct);
        return true;
    }

    /// <summary>Merges a scan into the saved list: known lights get their new address, new ones are added.</summary>
    public static List<LightConfig> Merge(IEnumerable<LightConfig> saved, IEnumerable<FoundLight> found)
    {
        var list = saved.Select(l => l).ToList();
        foreach (var f in found)
        {
            var known = list.FirstOrDefault(l => l.Kind == f.Kind && l.Id == f.Id);
            if (known != null)
            {
                known.Ip = f.Ip;
                if (string.IsNullOrEmpty(known.Model)) known.Model = f.Model;
                continue;
            }
            var count = list.Count(l => l.Kind == f.Kind) + 1;
            list.Add(new LightConfig
            {
                Kind = f.Kind,
                Id = f.Id,
                Ip = f.Ip,
                Model = f.Model,
                Name = f.Kind == LightKind.Wiz ? $"WiZ bulb {count}" : $"Govee {f.Model} {count}",
                // The WiZ ceiling light is the one that dims; Govee lights wait until you choose.
                Action = f.Kind == LightKind.Wiz ? LightAction.Dim : LightAction.Ignore,
                Level = 10,
            });
        }
        return list;
    }
}
