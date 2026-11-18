namespace NuvioLights;

public enum Mood { Normal, Dimmed }

/// <summary>
/// Decides when the lights go down and when they come back, from Nuvio's playback updates.
/// Pure and clock-driven so it can be tested: feed it updates with <see cref="Update"/> and call
/// <see cref="Tick"/> regularly; Tick returns a mood when the lights should change.
///
/// Rules: playing (a film, show, Live TV or, if allowed, music) dims after a short settle; a pause restores
/// almost at once; nothing playing (IDLE) or Nuvio going away restores after a longer wait, so the gap between
/// two episodes, or between two songs, does not flash the room.
/// </summary>
public sealed class Director
{
    private readonly Func<Settings> settings;
    private Mood current = Mood.Normal;
    private Mood? pending;
    private DateTime dueAt;

    public Director(Func<Settings> settings) => this.settings = settings;

    public Mood Current => current;

    /// <summary>A playback update from Nuvio; null when nothing is playing or Nuvio disconnected.</summary>
    public void Update(Playback? playback, DateTime now)
    {
        var s = settings();
        var wanted = Wants(playback, s) ? Mood.Dimmed : Mood.Normal;
        if (wanted == current)
        {
            pending = null;
            return;
        }
        var delay = wanted == Mood.Dimmed
            ? s.DimDelayMs
            : playback == null ? s.IdleRestoreDelayMs : s.PauseRestoreDelayMs;
        // A change already waiting in the same direction keeps its time, so repeated updates don't push it back.
        if (pending == wanted) return;
        pending = wanted;
        dueAt = now.AddMilliseconds(delay);
    }

    /// <summary>Returns the new mood when it is due, otherwise null.</summary>
    public Mood? Tick(DateTime now)
    {
        if (pending is not { } next || now < dueAt) return null;
        pending = null;
        current = next;
        return next;
    }

    /// <summary>Forces a mood straight away (the tray's test items, turning the app off, exiting).</summary>
    public void Force(Mood mood)
    {
        pending = null;
        current = mood;
    }

    public static bool Wants(Playback? playback, Settings s)
    {
        if (!s.Enabled || playback is not { Playing: true }) return false;
        return playback.IsMusic ? s.Music : s.Video;
    }
}
