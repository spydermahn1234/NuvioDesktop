using Microsoft.Win32;

namespace NuvioLights.App;

/// <summary>Start with Windows, through the current user's Run key (no admin needed).</summary>
internal static class AutoStart
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string Name = "NuvioLights";

    public static bool IsOn
    {
        get
        {
            using var key = Registry.CurrentUser.OpenSubKey(RunKey);
            return key?.GetValue(Name) is string;
        }
    }

    public static void Set(bool on)
    {
        using var key = Registry.CurrentUser.CreateSubKey(RunKey);
        if (on) key.SetValue(Name, $"\"{Environment.ProcessPath}\"");
        else key.DeleteValue(Name, throwOnMissingValue: false);
    }
}
