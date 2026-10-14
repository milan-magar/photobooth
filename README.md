# Photo Booth

A Java Swing re-creation of the 2005 Apple Photo Booth (introduced with the iMac G5 and iSight camera). Features a live 3×3 grid of real-time effects, a filmstrip carousel, and a shutter sequence with countdown and flash — sourced from either a USB webcam or from your phone over Wi-Fi.

## Features

- **Live video grid** — nine simultaneous previews, one per effect
- **Eight effects** — Normal, X-Ray, Sepia, Thermal Vision, Pop Art, Bulge, Squeeze, Twirl
- **Shutter sequence** — 3-2-1 countdown overlay, white flash, optional camera-shutter sound
- **Filmstrip** — captured photos appear as white-bordered thumbnails; click to enlarge
- **Two video sources**
  - **Webcam** — any USB or built-in camera via `webcam-capture`
  - **Phone** — any device that POSTs JPEG frames to `http://<your-pc-ip>:8080/upload`

## Requirements

- Java 17 or newer (tested on Java 25 / Eclipse Adoptium)
- Maven 3.9+ (`mvn -version` should work)
- Optional: a webcam, or a phone app that can POST JPEG frames

## Build

```bash
mvn clean package
```

Or on Windows, just double-click `build.bat`.

## Run

```bash
java -jar target/photobooth-1.0.0.jar
```

Or on Windows, double-click `run.bat`.

You'll be asked to choose a video source:

1. **Use Webcam** — streams from the default webcam at 30 FPS
2. **Use Phone (port 8080)** — listens for JPEG frames over HTTP

## Phone mode — how to send frames

The app listens on port **8080**. Send JPEG frames with a simple POST:

```bash
curl --data-binary @photo.jpg http://<your-pc-ip>:8080/upload
```

Or from an Android/iOS app, POST the JPEG bytes to:

```
http://<your-pc-ip>:8080/upload
Content-Type: image/jpeg
```

Find your PC's LAN IP from the dialog that appears when Phone mode starts, or run `ipconfig` (Windows) / `ifconfig` (macOS/Linux).

A live-status timer in the window title shows how many frames have arrived and how long ago the last one was.

### Test from a browser

Open `http://<your-pc-ip>:8080/` to see a landing page with a frame counter and a manual upload form.

### Optional endpoints

- `GET /latest` — returns the most recent frame as `image/jpeg` (204 until a frame arrives)

## Project structure

```
src/main/java/com/photobooth/
├── PhotoBoothMain.java       — entry point, source-selection dialog
├── FrameSource.java          — interface for video sources
├── WebcamFrameSource.java    — webcam-capture backend
├── HttpFrameSource.java      — HTTP listener on port 8080
├── CaptureWorker.java        — background frame-poller
├── FilterEngine.java         — pixel-level effect algorithms
├── EffectsGridPanel.java     — the 3×3 live effect grid
├── ShutterBar.java           — middle control bar with shutter button
├── ShutterButton.java        — custom round metallic shutter
└── FilmStripPanel.java       — bottom thumbnail carousel
```

## Windows Firewall

The first time Phone mode starts, Windows will ask to allow network access. Click **Allow** for Private networks. If you missed it, add a rule manually:

```
netsh advfirewall firewall add rule name="PhotoBooth 8080" dir=in action=allow protocol=TCP localport=8080
```

## Captures

Snapped photos are saved as PNGs in a `captures/` folder next to the JAR (created on first capture).

## License

MIT — see `LICENSE` if you add one.