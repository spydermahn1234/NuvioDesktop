using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;

namespace NuvioLights.App;

/// <summary>The tray icon, drawn in code: a bulb that is warm when the room is lit, dark blue while dimmed, grey without Nuvio.</summary>
internal static class Icons
{
    public static readonly Icon Lit = Bulb(Color.FromArgb(255, 196, 64), Color.FromArgb(255, 236, 170));
    public static readonly Icon Dimmed = Bulb(Color.FromArgb(70, 90, 200), Color.FromArgb(140, 160, 255));
    public static readonly Icon Offline = Bulb(Color.FromArgb(120, 120, 120), Color.FromArgb(180, 180, 180));

    private static Icon Bulb(Color fill, Color glow)
    {
        using var bmp = new Bitmap(32, 32);
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.Transparent);
            using var path = new GraphicsPath();
            path.AddEllipse(5, 2, 22, 22);
            using var brush = new PathGradientBrush(path) { CenterColor = glow, SurroundColors = new[] { fill } };
            g.FillPath(brush, path);
            using var outline = new Pen(Color.FromArgb(200, 40, 40, 40), 1.5f);
            g.DrawEllipse(outline, 5, 2, 22, 22);
            using var cap = new SolidBrush(Color.FromArgb(90, 90, 90));
            g.FillRectangle(cap, 11, 23, 10, 3);
            g.FillRectangle(cap, 12, 27, 8, 3);
        }
        var handle = bmp.GetHicon();
        var icon = (Icon)Icon.FromHandle(handle).Clone();
        DestroyIcon(handle);
        return icon;
    }

    [DllImport("user32.dll")]
    private static extern bool DestroyIcon(IntPtr handle);
}
