# Lyrics (Android Auto)

Shows synced lyrics for whatever Spotify is playing, on the phone and on the Android Auto screen.
Lyrics come from [LRCLIB](https://lrclib.net); who sings which line comes from [Genius](https://genius.com)
section headers. Personal sideloaded app; not for the Play Store.

## Install

1. Install Android Studio, open this folder, let Gradle sync.
2. Phone: Settings → About phone → Software information → tap **Build number** 7×, then turn on **USB debugging** in Developer options.
3. Plug the phone in, press **Run** in Android Studio.
4. Open the **Lyrics** app on the phone:
   - **Grant access** → enable *Lyrics* (notification access, used to read Spotify's playback).
   - **Allow background** → so One UI doesn't kill it.
5. Android Auto: Settings → Apps → Android Auto → *Additional settings in the app* → scroll down,
   tap **Version** 10× to enable developer mode → ⋮ → **Developer settings** → enable **Unknown sources**.
6. Connect to the car. **Lyrics** appears in the Android Auto app launcher.

## Using it

- Car screen has two layouts (pick on the phone): **Big line** (`MessageTemplate`) or **Line + next** (`PaneTemplate`).
  If one stops updating or errors in the car, switch to the other.
- **Offset**: if lyrics run late over the car audio, press **+** a few times.
- Lyrics are cached, so songs you've played before work without signal.
- **Singer colors**: on songs with more than one singer, each singer's lines get their own color
  (blue, red, green, yellow), and lines sung together blend them. The phone shows which color is whom;
  the car's **Line + next** layout writes the singer's name under each line. Colors show up a moment after
  the lyrics, once Genius answers, and stay cached like the lyrics.

## Code map

| Folder | What |
|---|---|
| `media/` | `SpotifyListenerService` reads Spotify's media session → `SpotifyPlayback` flows |
| `lyrics/` | LRCLIB client, LRC parser, Room cache, `LyricsRepository` |
| `singers/` | Genius client and cache, section parser, word-by-word matching onto synced lines, `CreditSingersUseCase` |
| `sync/` | `LyricsEngine`: track → lyrics → singers → current line index |
| `car/` | Android Auto service, session and screen |
| `ui/` | Phone screen (Compose) |
