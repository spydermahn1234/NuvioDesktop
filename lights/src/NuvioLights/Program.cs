namespace NuvioLights.App;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        using var single = new Mutex(true, @"Local\NuvioLights", out var first);
        if (!first) return; // Already running in the tray.
        ApplicationConfiguration.Initialize();
        Log.FilePath = Path.Combine(Path.GetDirectoryName(Settings.DefaultPath)!, "nuvio-lights.log");
        Directory.CreateDirectory(Path.GetDirectoryName(Settings.DefaultPath)!);
        Log.Write("Nuvio Lights starting");
        Application.Run(new TrayApp());
    }
}
