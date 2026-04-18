# Rivestream CloudStream Extension

A CloudStream 4.x extension for integrating with the Rivestream API to stream and download movies and TV episodes.

## Features

- Search support for movies and TV shows
- Streaming and downloading link support
- TV season/episode support
- Mandatory `tmdbId` validation for API calls
- English language support
- Configurable Rivestream API endpoint (`https://api.rivestream.org` by default)
- User-friendly endpoint validation and graceful failure handling

## Project Structure

- `build.gradle.kts` - Build config (Kotlin, Java 8, dependencies)
- `settings.gradle.kts` - Gradle settings
- `src/main/kotlin/com/rivestream/RivestreamExtension.kt` - Main extension implementation
- `src/main/kotlin/com/rivestream/RivestreamAPI.kt` - API client
- `src/main/kotlin/com/rivestream/models/*` - Data models
- `src/main/res/values/strings.xml` - Extension metadata strings
- `repository.json` - CloudStream repository manifest

## API Notes

- Base endpoint is configurable and must be a valid HTTP(S) URL.
- `tmdbId` is required.
- TV stream calls require `season` and `episode`.
- Returned links include quality, language, URL, and download flag.

## Build

```bash
./gradlew clean build
```

## Usage (Code)

```kotlin
val extension = RivestreamExtension()
extension.updateEndpoint("https://api.rivestream.org")

val results = extension.search("interstellar", page = 1)

val movieLinks = extension.getStreamUrls(
    Content(
        tmdbId = 157336,
        title = "Interstellar",
        type = ContentType.MOVIE,
    )
)
```

TV example:

```kotlin
val episodeLinks = extension.getStreamUrls(
    Content(
        tmdbId = 1399,
        title = "Game of Thrones",
        type = ContentType.TVSHOW,
        season = 1,
        episode = 1,
    )
)
```

## Endpoint Configuration

Use `updateEndpoint(...)` to set custom Rivestream servers. Invalid values keep the previous endpoint and return validation feedback.

## License

MIT
