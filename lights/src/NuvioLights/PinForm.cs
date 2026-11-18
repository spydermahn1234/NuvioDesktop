namespace NuvioLights.App;

/// <summary>Asks for Nuvio's six-digit phone-remote code, once.</summary>
internal sealed class PinForm : Form
{
    private readonly TextBox code = new() { Width = 160, MaxLength = 12, Font = new Font("Segoe UI", 16f), TextAlign = HorizontalAlignment.Center };

    public PinForm(string problem)
    {
        Text = "Pair Nuvio Lights with Nuvio";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        TopMost = true;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
        Padding = new Padding(16);
        Font = new Font("Segoe UI", 10f);
        Icon = Icons.Lit;

        var layout = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, AutoSize = true, WrapContents = false };
        if (!string.IsNullOrEmpty(problem))
            layout.Controls.Add(new Label { Text = problem, AutoSize = true, ForeColor = Color.Firebrick });
        layout.Controls.Add(new Label
        {
            Text = "In Nuvio, open Settings › Keyboard shortcuts › Phone remote\n(turn it on if it is off) and type the six-digit code it shows:",
            AutoSize = true,
            MaximumSize = new Size(420, 0),
        });
        layout.Controls.Add(code);
        var buttons = new FlowLayoutPanel { FlowDirection = FlowDirection.RightToLeft, AutoSize = true, Dock = DockStyle.Fill };
        var ok = new Button { Text = "Pair", DialogResult = DialogResult.OK, AutoSize = true };
        var cancel = new Button { Text = "Not now", DialogResult = DialogResult.Cancel, AutoSize = true };
        buttons.Controls.Add(ok);
        buttons.Controls.Add(cancel);
        layout.Controls.Add(buttons);
        Controls.Add(layout);
        AcceptButton = ok;
        CancelButton = cancel;
        Shown += (_, _) => { Activate(); code.Focus(); };
    }

    public string Code => code.Text;
}
