namespace NuvioLights;

/// <summary>Ties it together: Nuvio's playback in, the director's decision, the lights out.</summary>
public sealed class Engine
{
    private readonly object gate = new();
    private Settings settings;
    private readonly string settingsPath;
    private readonly Director director;
    private readonly CancellationTokenSource stop = new();
    private Task? linkTask;
    private Task? tickTask;
    private Task applying = Task.CompletedTask;

    public Engine(string settingsPath, ILightFactory? factory = null)
    {
        this.settingsPath = settingsPath;
        settings = Settings.Load(settingsPath);
        director = new Director(() => Settings);
        var dir = Path.GetDirectoryName(settingsPath)!;
        Factory = factory ?? new LanLightFactory();
        Rig = new LightRig(() => Settings, Factory, Path.Combine(dir, "dimmed.json"));
        Link = new NuvioLink(() => Settings.NuvioHost, () => Settings.NuvioToken);
        Link.Paired += (token, name) => Change(s => { s.NuvioToken = token; s.NuvioName = name; });
        Link.PlaybackChanged += p =>
        {
            Playback = p;
            lock (gate) director.Update(p, DateTime.UtcNow);
            Changed?.Invoke();
        };
        Link.StatusChanged += (_, text) =>
        {
            StatusText = text;
            Changed?.Invoke();
        };
    }

    public NuvioLink Link { get; }
    public LightRig Rig { get; }
    public ILightFactory Factory { get; }
    public Playback? Playback { get; private set; }
    public string StatusText { get; private set; } = "Starting";
    public Mood Mood => director.Current;

    /// <summary>Raised (on a worker thread) when the status, playback or mood changes.</summary>
    public event Action? Changed;

    public Settings Settings
    {
        get { lock (gate) return settings; }
    }

    /// <summary>Changes the settings and saves them.</summary>
    public void Change(Action<Settings> edit)
    {
        lock (gate)
        {
            var copy = settings.Copy();
            edit(copy);
            settings = copy;
            copy.Save(settingsPath);
            director.Update(Playback, DateTime.UtcNow);
        }
        Changed?.Invoke();
    }

    public void Start()
    {
        // Left dimmed by a crash or a restart: put the room back before anything else.
        if (Rig.HasSaved) Apply(Mood.Normal);
        linkTask = Task.Run(() => Link.RunAsync(stop.Token));
        tickTask = Task.Run(TickAsync);
    }

    private async Task TickAsync()
    {
        while (!stop.IsCancellationRequested)
        {
            Mood? due;
            lock (gate) due = director.Tick(DateTime.UtcNow);
            if (due is { } mood)
            {
                Log.Write(mood == Mood.Dimmed ? $"Dimming for {Describe(Playback)}" : "Restoring the lights");
                Apply(mood);
                Changed?.Invoke();
            }
            try { await Task.Delay(100, stop.Token); } catch (OperationCanceledException) { break; }
        }
    }

    /// <summary>The tray's Test items: dim or restore now, whatever is playing.</summary>
    public void Force(Mood mood)
    {
        lock (gate) director.Force(mood);
        Apply(mood);
        Changed?.Invoke();
    }

    private void Apply(Mood mood)
    {
        applying = Rig.ApplyAsync(mood, stop.Token).ContinueWith(t =>
        {
            if (t.Exception != null) Log.Write($"Lights: {t.Exception.GetBaseException().Message}");
        });
    }

    /// <summary>Puts the lights back and stops. Waits up to a few seconds for the restore.</summary>
    public async Task ShutdownAsync()
    {
        if (director.Current == Mood.Dimmed || Rig.HasSaved)
        {
            lock (gate) director.Force(Mood.Normal);
            var restore = Rig.ApplyAsync(Mood.Normal, CancellationToken.None);
            await Task.WhenAny(restore, Task.Delay(5_000));
        }
        stop.Cancel();
        try { await Task.WhenAll(linkTask ?? Task.CompletedTask, tickTask ?? Task.CompletedTask).WaitAsync(TimeSpan.FromSeconds(2)); }
        catch (Exception) { }
    }

    public Task WhenApplied => applying;

    public static string Describe(Playback? p) => p == null ? "nothing" : $"{(p.IsMusic ? "music" : p.Kind)}: {p.Title}";
}
