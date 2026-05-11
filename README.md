# Rivestream CloudStream Extension

A CloudStream 4.x extension for integrating with the Rivestream API to stream and download movies and TV episodes.

## Features

- Search support for movies and TV shows
- TMDB-assisted search and TMDB ID resolution
- Streaming and downloading link support
- TV season/episode support
- Configurable Rivestream API endpoint
- Configurable quality, language, and cache preferences
- User-friendly validation and error messages

## Installation (CloudStream)

1. Build the plugin:
   ```bash
   ./gradlew clean build
   ```
2. Publish the generated plugin artifact to your plugin repo/release.
3. Add your `repository.json` URL in CloudStream repositories.
4. Install **Rivestream** from CloudStream extension settings.

## Configuration

Open extension settings and configure:

- **API Endpoint URL** (must be valid HTTP/HTTPS)
- **Default quality** (`Auto`, `1080p`, `720p`, `480p`)
- **Language** (for example `en` or `en-us`)
- **Cache settings** (enabled + cache duration)

## TMDB API Key Setup

TMDB integration is enabled when `TMDB_API_KEY` is available in environment variables during runtime/build context.

Example:

```bash
export TMDB_API_KEY="your_tmdb_api_key"
```

Without a TMDB key, the extension still works with Rivestream API data where available.

## Troubleshooting

- **Invalid endpoint**: verify URL starts with `http://` or `https://`.
- **No streams found**: confirm TMDB ID mapping exists and endpoint is reachable.
- **Rate limited**: wait and retry after the suggested backoff.
- **Network errors**: verify internet connectivity and server availability.

## Project Structure

- `build.gradle.kts` - Cloudstream gradle configuration + extension metadata
- `src/main/AndroidManifest.xml` - Minimal manifest for Cloudstream plugin module
- `src/main/kotlin/com/rivestream/RivestreamProvider.kt` - `@CloudstreamPlugin` entrypoint
- `src/main/kotlin/com/rivestream/RivestreamExtension.kt` - Main extension implementation
- `src/main/kotlin/com/rivestream/RivestreamSettings.kt` - Settings provider and settings UI models
- `src/main/kotlin/com/rivestream/TMDBClient.kt` - TMDB integration
- `src/main/kotlin/com/rivestream/ErrorHandler.kt` - Error handling and retry logic
- `src/main/kotlin/com/rivestream/models/*` - Data models
- `src/main/res/values/strings.xml` - UI and error strings
- `src/main/res/values/plugin.xml` - Plugin descriptor values

## License

MIT
