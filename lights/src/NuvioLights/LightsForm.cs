namespace NuvioLights.App;

/// <summary>Find the lights, name them, and choose what each does while Nuvio plays.</summary>
internal sealed class LightsForm : Form
{
    private static readonly (LightAction action, string label)[] Actions =
    {
        (LightAction.Dim, "Dim"),
        (LightAction.Off, "Turn off"),
        (LightAction.Ignore, "Leave alone"),
    };

    private readonly Engine engine;
    private readonly DataGridView grid = new()
    {
        Dock = DockStyle.Fill,
        AllowUserToAddRows = false,
        AllowUserToResizeRows = false,
        RowHeadersVisible = false,
        SelectionMode = DataGridViewSelectionMode.FullRowSelect,
        MultiSelect = false,
        AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill,
        BackgroundColor = SystemColors.Window,
        EditMode = DataGridViewEditMode.EditOnEnter,
    };
    private readonly Label status = new() { AutoSize = true, ForeColor = SystemColors.GrayText, Padding = new Padding(0, 6, 0, 0) };
    private readonly CheckBox video = new() { Text = "Films, shows, Live TV and videos", AutoSize = true };
    private readonly CheckBox music = new() { Text = "Music", AutoSize = true };
    private readonly NumericUpDown fade = new() { Minimum = 0, Maximum = 10, DecimalPlaces = 1, Increment = 0.5m, Width = 60 };
    private readonly TextBox host = new() { Width = 140 };
    private List<LightConfig> lights;

    public LightsForm(Engine engine)
    {
        this.engine = engine;
        var s = engine.Settings.Copy();
        lights = s.Lights;
        Text = "Nuvio Lights";
        Icon = Icons.Lit;
        Font = new Font("Segoe UI", 9.5f);
        StartPosition = FormStartPosition.CenterScreen;
        Size = new Size(820, 480);
        MinimumSize = new Size(640, 360);

        grid.Columns.Add(new DataGridViewTextBoxColumn { Name = "name", HeaderText = "Name", FillWeight = 30 });
        grid.Columns.Add(new DataGridViewTextBoxColumn { Name = "kind", HeaderText = "Type", ReadOnly = true, FillWeight = 22 });
        grid.Columns.Add(new DataGridViewTextBoxColumn { Name = "ip", HeaderText = "Address", ReadOnly = true, FillWeight = 18 });
        var action = new DataGridViewComboBoxColumn { Name = "action", HeaderText = "While playing", FillWeight = 18, FlatStyle = FlatStyle.Flat };
        action.Items.AddRange(Actions.Select(a => (object)a.label).ToArray());
        grid.Columns.Add(action);
        grid.Columns.Add(new DataGridViewTextBoxColumn { Name = "level", HeaderText = "Dim to %", FillWeight = 12 });
        grid.CellValidating += (_, e) =>
        {
            if (grid.Columns[e.ColumnIndex].Name != "level") return;
            if (!int.TryParse(e.FormattedValue?.ToString(), out var v) || v < 1 || v > 100)
            {
                status.Text = "Dim to % is a number from 1 to 100 (WiZ bulbs go no lower than 10).";
                e.Cancel = true;
            }
        };

        video.Checked = s.Video;
        music.Checked = s.Music;
        fade.Value = Math.Clamp(s.FadeMs / 1000m, 0, 10);
        host.Text = s.NuvioHost;

        var find = new Button { Text = "Find lights", AutoSize = true };
        var flash = new Button { Text = "Flash", AutoSize = true };
        var remove = new Button { Text = "Remove", AutoSize = true };
        var save = new Button { Text = "Save", AutoSize = true };
        var cancel = new Button { Text = "Cancel", AutoSize = true, DialogResult = DialogResult.Cancel };
        find.Click += async (_, _) => await FindAsync(find);
        flash.Click += async (_, _) => await FlashAsync(flash);
        remove.Click += (_, _) => Remove();
        save.Click += (_, _) => Save();

        var top = new FlowLayoutPanel { Dock = DockStyle.Top, AutoSize = true, Padding = new Padding(8), WrapContents = true };
        top.Controls.Add(new Label { Text = "Dim for:", AutoSize = true, Padding = new Padding(0, 4, 0, 0) });
        top.Controls.Add(video);
        top.Controls.Add(music);
        top.Controls.Add(new Label { Text = "   Fade (s):", AutoSize = true, Padding = new Padding(0, 4, 0, 0) });
        top.Controls.Add(fade);
        top.Controls.Add(new Label { Text = "   Nuvio PC:", AutoSize = true, Padding = new Padding(0, 4, 0, 0) });
        top.Controls.Add(host);

        var bottom = new FlowLayoutPanel { Dock = DockStyle.Bottom, AutoSize = true, Padding = new Padding(8), FlowDirection = FlowDirection.LeftToRight };
        bottom.Controls.AddRange(new Control[] { find, flash, remove, new Label { Width = 40 }, save, cancel, status });

        var hint = new Label
        {
            Dock = DockStyle.Top,
            AutoSize = true,
            Padding = new Padding(10, 0, 10, 6),
            MaximumSize = new Size(800, 0),
            ForeColor = SystemColors.GrayText,
            Text = "Lights come back to exactly how they were when you pause or stop. Govee lights need \"LAN Control\" " +
                   "turned on for each light in the Govee Home app before Find lights can see them.",
        };

        Controls.Add(grid);
        Controls.Add(hint);
        Controls.Add(top);
        Controls.Add(bottom);
        AcceptButton = save;
        CancelButton = cancel;
        Fill();
        if (lights.Count == 0) Shown += async (_, _) => await FindAsync(find);
    }

    private void Fill()
    {
        grid.Rows.Clear();
        foreach (var l in lights)
        {
            var kind = l.Kind == LightKind.Wiz ? "WiZ" : $"Govee {l.Model}".Trim();
            grid.Rows.Add(l.Name, kind, l.Ip, Actions.First(a => a.action == l.Action).label, l.Level.ToString());
        }
    }

    private void ReadBack()
    {
        grid.EndEdit();
        for (var i = 0; i < lights.Count && i < grid.Rows.Count; i++)
        {
            var row = grid.Rows[i];
            var l = lights[i];
            l.Name = (row.Cells["name"].Value?.ToString() ?? "").Trim().IfEmpty(l.Name);
            var label = row.Cells["action"].Value?.ToString();
            l.Action = Actions.FirstOrDefault(a => a.label == label).action;
            if (int.TryParse(row.Cells["level"].Value?.ToString(), out var level)) l.Level = Math.Clamp(level, 1, 100);
        }
    }

    private async Task FindAsync(Button button)
    {
        ReadBack();
        button.Enabled = false;
        status.Text = "Looking for WiZ and Govee lights…";
        try
        {
            var found = await Task.Run(() => engine.Factory.ScanAsync(CancellationToken.None));
            var before = lights.Count;
            lights = LightTools.Merge(lights, found);
            Fill();
            var added = lights.Count - before;
            status.Text = found.Count == 0
                ? "No lights answered. Are they on, and on the same Wi-Fi as this PC?"
                : $"Found {found.Count} light{(found.Count == 1 ? "" : "s")}" + (added > 0 ? $", {added} new. Use Flash to tell them apart." : ".");
        }
        catch (Exception e)
        {
            status.Text = $"Search failed: {e.Message}";
        }
        finally
        {
            button.Enabled = true;
        }
    }

    private async Task FlashAsync(Button button)
    {
        if (grid.CurrentRow is not { Index: var i } || i >= lights.Count) return;
        ReadBack();
        var light = engine.Factory.Create(lights[i]);
        button.Enabled = false;
        status.Text = $"Flashing {lights[i].Name}…";
        try
        {
            var ok = await Task.Run(() => LightTools.FlashAsync(light, CancellationToken.None));
            status.Text = ok ? "" : $"{lights[i].Name} did not answer.";
        }
        catch (Exception e)
        {
            status.Text = e.Message;
        }
        finally
        {
            button.Enabled = true;
        }
    }

    private void Remove()
    {
        if (grid.CurrentRow is not { Index: var i } || i >= lights.Count) return;
        ReadBack();
        lights.RemoveAt(i);
        Fill();
    }

    private void Save()
    {
        ReadBack();
        var newHost = host.Text.Trim().IfEmpty("127.0.0.1");
        var hostChanged = newHost != engine.Settings.NuvioHost;
        engine.Change(s =>
        {
            s.Lights = lights;
            s.Video = video.Checked;
            s.Music = music.Checked;
            s.FadeMs = (int)(fade.Value * 1000);
            s.NuvioHost = newHost;
        });
        if (hostChanged) engine.Link.Reconnect();
        DialogResult = DialogResult.OK;
        Close();
    }
}

internal static class StringExtras
{
    public static string IfEmpty(this string text, string fallback) => string.IsNullOrWhiteSpace(text) ? fallback : text;
}
