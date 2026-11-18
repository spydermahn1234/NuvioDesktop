using System.Text.Json;
using System.Text.Json.Serialization;

namespace NuvioLights;

public enum LightKind { Wiz, Govee }

/// <summary>What a light does while something plays: dim to <see cref="LightConfig.Level"/>, turn off, or nothing.</summary>
public enum LightAction { Dim, Off, Ignore }

public sealed class LightConfig
{
    public LightKind Kind { get; set; }
    /// <summary>The light's MAC address (WiZ) or device id (Govee): how it is found again if its IP changes.</summary>
    public string Id { get; set; } = "";
    public string Ip { get; set; } = "";
    public string Name { get; set; } = "";
    /// <summary>Govee model, e.g. H6163; WiZ module name. Shown only.</summary>
    public string Model { get; set; } = "";
    public LightAction Action { get; set; } = LightAction.Dim;
    /// <summary>Brightness while dimmed, 1 to 100 percent.</summary>
    public int Level { get; set; } = 10;
}

public sealed class Settings
{
    public bool Enabled { get; set; } = true;
    /// <summary>Films, shows, Live TV and YouTube videos.</summary>
    public bool Video { get; set; } = true;
    public bool Music { get; set; } = true;
    public int FadeMs { get; set; } = 1500;
    public int DimDelayMs { get; set; } = 800;
    public int PauseRestoreDelayMs { get; set; } = 300;
    public int IdleRestoreDelayMs { get; set; } = 4000;

    /// <summary>Where Nuvio runs. 127.0.0.1 when Nuvio Lights is on the same PC.</summary>
    public string NuvioHost { get; set; } = "127.0.0.1";
    /// <summary>The token Nuvio gave when this app was paired, like the phone remote's.</summary>
    public string? NuvioToken { get; set; }
    public string? NuvioName { get; set; }

    public List<LightConfig> Lights { get; set; } = new();

    /// <summary>Set after the first start, when the app turned on Start with Windows and opened the Lights window.</summary>
    public bool FirstRunDone { get; set; }

    private static readonly JsonSerializerOptions Json = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter() },
    };

    public static string DefaultPath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NuvioLights", "settings.json");

    public static Settings Load(string path)
    {
        try
        {
            if (File.Exists(path)) return JsonSerializer.Deserialize<Settings>(File.ReadAllText(path), Json) ?? new Settings();
        }
        catch (Exception e) when (e is JsonException or IOException)
        {
            Log.Write($"Settings unreadable, starting fresh: {e.Message}");
        }
        return new Settings();
    }

    public void Save(string path)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temp = path + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(this, Json));
        File.Move(temp, path, overwrite: true);
    }

    public Settings Copy() => JsonSerializer.Deserialize<Settings>(JsonSerializer.Serialize(this, Json), Json)!;
}
