namespace NuvioLights;

/// <summary>A small rolling log next to the settings, for "Open log" in the tray menu.</summary>
public static class Log
{
    private static readonly object Gate = new();
    public static string? FilePath { get; set; }

    public static void Write(string message)
    {
        var line = $"{DateTime.Now:yyyy-MM-dd HH:mm:ss} {message}";
        System.Diagnostics.Debug.WriteLine(line);
        if (FilePath == null) return;
        lock (Gate)
        {
            try
            {
                var info = new FileInfo(FilePath);
                if (info.Exists && info.Length > 512 * 1024) File.Move(FilePath, FilePath + ".old", overwrite: true);
                File.AppendAllText(FilePath, line + Environment.NewLine);
            }
            catch (IOException)
            {
            }
        }
    }
}
