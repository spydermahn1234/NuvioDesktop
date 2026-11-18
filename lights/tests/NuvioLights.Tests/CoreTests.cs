namespace NuvioLights.Tests;

public class NuvioLinesTest
{
    [Fact]
    public void ReadsAFilmState()
    {
        // As PhoneRemoteProtocol.stateLine writes it.
        var line = "STATE film 1 1000 7200000 50 100 0 Blade%20Runner%202049 S01E02%20%C2%B7%20Pilot - - - 100 - 0 0 - - -";
        Assert.True(NuvioLines.TryParseState(line, out var p));
        Assert.Equal(new Playback("film", true, "Blade Runner 2049"), p);
        Assert.False(p!.IsMusic);
    }

    [Fact]
    public void ReadsPausedMusicAndIdle()
    {
        Assert.True(NuvioLines.TryParseState("STATE music 0 0 294000 - 100 1 Billie%20Jean - -", out var p));
        Assert.True(p!.IsMusic);
        Assert.False(p.Playing);
        Assert.True(NuvioLines.TryParseState("IDLE", out var idle));
        Assert.Null(idle);
    }

    [Fact]
    public void IgnoresOtherLines()
    {
        Assert.False(NuvioLines.TryParseState("THEME FF0000 - 000000", out _));
        Assert.False(NuvioLines.TryParseState("PONG", out _));
    }

    [Fact]
    public void ReadsTheDiscoveryReplyAndWritesHello()
    {
        Assert.True(NuvioLines.TryParseDiscoveryReply("NUVIO_REMOTE 1 47322 Living%20Room", out var port, out var name));
        Assert.Equal(47322, port);
        Assert.Equal("Living Room", name);
        Assert.Equal("HELLO 1 - Nuvio%20Lights", NuvioLines.Hello(null, "Nuvio Lights"));
        Assert.Equal("HELLO 1 abc Nuvio%20Lights", NuvioLines.Hello("abc", "Nuvio Lights"));
    }
}

public class DirectorTest
{
    private static readonly DateTime T0 = new(2026, 11, 18, 20, 0, 0, DateTimeKind.Utc);
    private readonly Settings settings = new();
    private Director NewDirector() => new(() => settings);
    private static Playback Film(bool playing) => new("film", playing, "Dune");

    [Fact]
    public void DimsShortlyAfterPlayStarts()
    {
        var d = NewDirector();
        d.Update(Film(true), T0);
        Assert.Null(d.Tick(T0.AddMilliseconds(500)));
        Assert.Equal(Mood.Dimmed, d.Tick(T0.AddMilliseconds(900)));
        Assert.Null(d.Tick(T0.AddSeconds(5)));
    }

    [Fact]
    public void RestoresAlmostAtOnceOnPause()
    {
        var d = NewDirector();
        d.Update(Film(true), T0);
        d.Tick(T0.AddSeconds(1));
        d.Update(Film(false), T0.AddSeconds(10));
        Assert.Equal(Mood.Normal, d.Tick(T0.AddSeconds(10.4)));
    }

    [Fact]
    public void TheGapBetweenEpisodesDoesNotFlashTheRoom()
    {
        var d = NewDirector();
        d.Update(Film(true), T0);
        d.Tick(T0.AddSeconds(1));
        d.Update(null, T0.AddSeconds(60));          // episode ends
        Assert.Null(d.Tick(T0.AddSeconds(62)));
        d.Update(Film(true), T0.AddSeconds(63));    // next one starts
        Assert.Null(d.Tick(T0.AddSeconds(70)));
        Assert.Equal(Mood.Dimmed, d.Current);
    }

    [Fact]
    public void StoppingRestoresAfterTheWait()
    {
        var d = NewDirector();
        d.Update(Film(true), T0);
        d.Tick(T0.AddSeconds(1));
        d.Update(null, T0.AddSeconds(60));
        Assert.Equal(Mood.Normal, d.Tick(T0.AddSeconds(64.1)));
    }

    [Fact]
    public void RepeatedUpdatesDoNotPushTheChangeBack()
    {
        var d = NewDirector();
        d.Update(Film(true), T0);
        d.Update(Film(true), T0.AddMilliseconds(600));
        Assert.Equal(Mood.Dimmed, d.Tick(T0.AddMilliseconds(850)));
    }

    [Fact]
    public void MusicVideoAndOffFollowTheSettings()
    {
        Assert.True(Director.Wants(new Playback("music", true, "x"), settings));
        settings.Music = false;
        Assert.False(Director.Wants(new Playback("music", true, "x"), settings));
        Assert.True(Director.Wants(Film(true), settings));
        settings.Enabled = false;
        Assert.False(Director.Wants(Film(true), settings));
    }
}

public class LightRigTest
{
    private readonly Settings settings = new() { FadeMs = 0 };
    private readonly FakeFactory factory = new();
    private readonly string dir = Directory.CreateTempSubdirectory("nuviolights").FullName;

    private LightRig NewRig() => new(() => settings, factory, Path.Combine(dir, "dimmed.json"));

    private FakeLight Light(string id, LightAction action = LightAction.Dim, int level = 10, int min = 1)
    {
        var config = new LightConfig { Kind = LightKind.Wiz, Id = id, Ip = "10.0.0." + id, Name = id, Action = action, Level = level };
        settings.Lights.Add(config);
        return factory.Add(new LightConfig { Kind = config.Kind, Id = id, Ip = config.Ip, Name = id }, min);
    }

    [Fact]
    public async Task DimsThenPutsBackExactlyWhatWasThere()
    {
        var ceiling = Light("ceiling", level: 10, min: 10);
        ceiling.Brightness = 73;
        var rig = NewRig();
        await rig.ApplyAsync(Mood.Dimmed, default);
        Assert.Equal(10, ceiling.Brightness);
        await rig.ApplyAsync(Mood.Normal, default);
        Assert.Equal(73, ceiling.Brightness);
        Assert.True(ceiling.On);
        Assert.False(rig.HasSaved);
    }

    [Fact]
    public async Task LeavesAnOffLightAloneAndIgnoredLightsUntouched()
    {
        var off = Light("off");
        off.On = false;
        var ignored = Light("strip", LightAction.Ignore);
        var rig = NewRig();
        await rig.ApplyAsync(Mood.Dimmed, default);
        await rig.ApplyAsync(Mood.Normal, default);
        Assert.Empty(off.Calls);
        Assert.Empty(ignored.Calls);
    }

    [Fact]
    public async Task NeverBrightensALightAlreadyBelowTheLevel()
    {
        var lamp = Light("lamp", level: 30);
        lamp.Brightness = 20;
        var rig = NewRig();
        await rig.ApplyAsync(Mood.Dimmed, default);
        Assert.Equal(20, lamp.Brightness);
    }

    [Fact]
    public async Task OffActionTurnsItOffAndBackOn()
    {
        var strip = Light("strip", LightAction.Off);
        strip.Brightness = 60;
        var rig = NewRig();
        await rig.ApplyAsync(Mood.Dimmed, default);
        Assert.False(strip.On);
        await rig.ApplyAsync(Mood.Normal, default);
        Assert.True(strip.On);
        Assert.Equal(60, strip.Brightness);
    }

    [Fact]
    public async Task ALightSwitchedOffDuringTheFilmStaysOff()
    {
        var ceiling = Light("ceiling");
        var rig = NewRig();
        await rig.ApplyAsync(Mood.Dimmed, default);
        ceiling.On = false;
        ceiling.Calls.Clear();
        await rig.ApplyAsync(Mood.Normal, default);
        Assert.False(ceiling.On);
        Assert.Empty(ceiling.Calls);
    }

    [Fact]
    public async Task ACutShortRestoreKeepsTheFirstReading()
    {
        var ceiling = Light("ceiling", min: 10);
        ceiling.Brightness = 80;
        var rig = NewRig();
        await rig.ApplyAsync(Mood.Dimmed, default);
        ceiling.Brightness = 45; // part way back up when play resumed
        settings.Lights[0].Action = LightAction.Dim;
        // Simulate the restore being interrupted: the reading is still saved, so dim again.
        await rig.ApplyAsync(Mood.Dimmed, default);
        Assert.Equal(10, ceiling.Brightness);
        await rig.ApplyAsync(Mood.Normal, default);
        Assert.Equal(80, ceiling.Brightness);
    }

    [Fact]
    public async Task RestoresAfterARestart()
    {
        var ceiling = Light("ceiling");
        ceiling.Brightness = 90;
        await NewRig().ApplyAsync(Mood.Dimmed, default);
        var again = NewRig();
        Assert.True(again.HasSaved);
        await again.ApplyAsync(Mood.Normal, default);
        Assert.Equal(90, ceiling.Brightness);
    }

    [Fact]
    public async Task FindsALightThatMovedAddress()
    {
        var ceiling = Light("ceiling");
        ceiling.Brightness = 50;
        ceiling.ActualIp = "10.0.0.99";
        factory.Found.Add(new FoundLight(LightKind.Wiz, "ceiling", "10.0.0.99", "WiZ"));
        await NewRig().ApplyAsync(Mood.Dimmed, default);
        Assert.Equal(10, ceiling.Brightness);
        Assert.Equal("10.0.0.99", settings.Lights[0].Ip);
    }

    [Fact]
    public async Task FadesInSteps()
    {
        var light = new FakeLight(new LightConfig { Id = "x" });
        await LightRig.FadeAsync(light, 100, 10, 600, default);
        Assert.Equal(new[] { "b78", "b55", "b33", "b10" }, light.Calls);
    }
}

public class LightToolsTest
{
    [Fact]
    public void MergeUpdatesKnownAddressesAndAddsNewLightsWithDefaults()
    {
        var saved = new List<LightConfig> { new() { Kind = LightKind.Wiz, Id = "m1", Ip = "1.1.1.1", Name = "Ceiling", Level = 20 } };
        var merged = LightTools.Merge(saved, new[]
        {
            new FoundLight(LightKind.Wiz, "m1", "1.1.1.9", "WiZ"),
            new FoundLight(LightKind.Govee, "g1", "1.1.1.5", "H6163"),
        });
        Assert.Equal(2, merged.Count);
        Assert.Equal("1.1.1.9", merged[0].Ip);
        Assert.Equal("Ceiling", merged[0].Name);
        Assert.Equal(20, merged[0].Level);
        Assert.Equal(LightAction.Ignore, merged[1].Action);
        Assert.Equal("Govee H6163 1", merged[1].Name);
    }
}
