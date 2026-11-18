using System.Diagnostics;
using Microsoft.Win32;

namespace NuvioLights.App;

/// <summary>The tray icon and its menu. All the work is in <see cref="Engine"/>.</summary>
internal sealed class TrayApp : ApplicationContext
{
    private readonly Engine engine = new(Settings.DefaultPath);
    private readonly Control ui = new();
    private readonly NotifyIcon tray = new() { Icon = Icons.Offline, Text = "Nuvio Lights", Visible = true };
    private readonly ToolStripMenuItem statusItem = new() { Enabled = false };
    private readonly ToolStripMenuItem playingItem = new() { Enabled = false };
    private readonly ToolStripMenuItem enabledItem = new("Dim the lights with Nuvio") { CheckOnClick = true };
    private readonly ToolStripMenuItem videoItem = new("Films, shows and videos") { CheckOnClick = true };
    private readonly ToolStripMenuItem musicItem = new("Music") { CheckOnClick = true };
    private readonly ToolStripMenuItem autoStartItem = new("Start with Windows") { CheckOnClick = true };
    private readonly HashSet<string> warned = new();
    private LightsForm? lightsForm;
    private bool pinOpen;
    private bool exiting;

    public TrayApp()
    {
        ui.CreateControl();

        var menu = new ContextMenuStrip();
        menu.Items.Add(statusItem);
        menu.Items.Add(playingItem);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add(enabledItem);
        menu.Items.Add(videoItem);
        menu.Items.Add(musicItem);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Lights…", null, (_, _) => ShowLights());
        menu.Items.Add("Pair with Nuvio again…", null, (_, _) => Repair());
        var test = new ToolStripMenuItem("Test");
        test.DropDownItems.Add("Dim now", null, (_, _) => engine.Force(Mood.Dimmed));
        test.DropDownItems.Add("Restore now", null, (_, _) => engine.Force(Mood.Normal));
        menu.Items.Add(test);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add(autoStartItem);
        menu.Items.Add("Open log", null, (_, _) => OpenLog());
        menu.Items.Add("Exit (restores the lights)", null, async (_, _) => await ExitAsync());
        menu.Opening += (_, _) => Refresh();
        tray.ContextMenuStrip = menu;
        tray.DoubleClick += (_, _) => ShowLights();

        enabledItem.CheckedChanged += (_, _) => Toggle(s => s.Enabled = enabledItem.Checked);
        videoItem.CheckedChanged += (_, _) => Toggle(s => s.Video = videoItem.Checked);
        musicItem.CheckedChanged += (_, _) => Toggle(s => s.Music = musicItem.Checked);
        autoStartItem.CheckedChanged += (_, _) =>
        {
            if (autoStartItem.Checked != AutoStart.IsOn) AutoStart.Set(autoStartItem.Checked);
        };

        engine.Changed += () => OnUi(Refresh);
        engine.Rig.Unreachable += name => OnUi(() =>
        {
            if (warned.Add(name)) tray.ShowBalloonTip(5000, "Nuvio Lights", $"{name} did not answer. Is it switched on at the wall?", ToolTipIcon.Warning);
        });
        engine.Link.AskPin = AskPinAsync;

        SystemEvents.SessionEnding += (_, _) => Task.Run(() => engine.ShutdownAsync()).Wait(5_000);
        Application.ApplicationExit += (_, _) => tray.Visible = false;

        engine.Start();
        Refresh();

        if (!engine.Settings.FirstRunDone)
        {
            engine.Change(s => s.FirstRunDone = true);
            AutoStart.Set(true);
            tray.ShowBalloonTip(6000, "Nuvio Lights is running", "It lives here in the tray. Pick your lights, then pair with Nuvio.", ToolTipIcon.Info);
            OnUi(ShowLights);
        }
    }

    private bool refreshing;

    private void Refresh()
    {
        refreshing = true;
        try
        {
            var s = engine.Settings;
            statusItem.Text = engine.StatusText;
            playingItem.Text = engine.Playback is { } p
                ? $"{(p.Playing ? "Playing" : "Paused")}: {Short(p.Title)}"
                : "Nothing playing";
            enabledItem.Checked = s.Enabled;
            videoItem.Checked = s.Video;
            musicItem.Checked = s.Music;
            videoItem.Enabled = musicItem.Enabled = s.Enabled;
            autoStartItem.Checked = AutoStart.IsOn;
            var connected = engine.Link.Status == LinkStatus.Connected;
            tray.Icon = !connected ? Icons.Offline : engine.Mood == Mood.Dimmed ? Icons.Dimmed : Icons.Lit;
            var tip = $"Nuvio Lights: {(connected ? engine.Mood == Mood.Dimmed ? "lights dimmed" : "lights normal" : engine.StatusText)}";
            tray.Text = tip.Length > 63 ? tip[..63] : tip;
        }
        finally
        {
            refreshing = false;
        }
    }

    private void Toggle(Action<Settings> edit)
    {
        if (refreshing) return;
        engine.Change(edit);
    }

    private static string Short(string title) => string.IsNullOrEmpty(title) ? "something" : title.Length > 40 ? title[..40] + "…" : title;

    private Task<string?> AskPinAsync(string problem)
    {
        var answer = new TaskCompletionSource<string?>();
        OnUi(() =>
        {
            if (pinOpen) { answer.TrySetResult(null); return; }
            pinOpen = true;
            try
            {
                using var form = new PinForm(problem);
                answer.TrySetResult(form.ShowDialog() == DialogResult.OK ? form.Code : null);
            }
            finally
            {
                pinOpen = false;
            }
        });
        return answer.Task;
    }

    private void Repair()
    {
        engine.Change(s => s.NuvioToken = null);
        engine.Link.PairAgain();
        engine.Link.Reconnect();
    }

    private void ShowLights()
    {
        if (lightsForm is { IsDisposed: false })
        {
            lightsForm.Activate();
            return;
        }
        lightsForm = new LightsForm(engine);
        lightsForm.FormClosed += (_, _) => lightsForm = null;
        lightsForm.Show();
    }

    private static void OpenLog()
    {
        if (Log.FilePath != null && File.Exists(Log.FilePath))
            Process.Start(new ProcessStartInfo(Log.FilePath) { UseShellExecute = true });
    }

    private async Task ExitAsync()
    {
        if (exiting) return;
        exiting = true;
        tray.Text = "Nuvio Lights: putting the lights back…";
        await engine.ShutdownAsync();
        tray.Visible = false;
        ExitThread();
    }

    private void OnUi(Action action)
    {
        if (ui.IsDisposed) return;
        if (ui.InvokeRequired) ui.BeginInvoke(action);
        else action();
    }
}
