# Android Local Development

## Prerequisites

- Android Studio compatible with the committed Android Gradle Plugin
- Android SDK selected by the project
- JDK compatible with the committed Gradle/AGP versions
- Git
- Android emulator or physical device

Do not commit `local.properties`, SDK paths, signing keys, or device-specific configuration.

## Baseline Gate

Before feature work, verify the committed SDK and dependency alignment. The baseline gate is:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
```

Both commands must pass on a clean checkout.

## Backend URL

For the Android emulator, a backend running on the host is normally reached through:

```text
http://10.0.2.2:8080/api/v1
```

A physical device requires a reachable host address or staging URL. Do not hardcode a developer's IP address in source control.

Use build variants or generated configuration for:

- `local`
- `staging`
- `production-demo`

## Run

1. Start PostgreSQL and the Spring Boot backend.
2. Select the local Android build variant.
3. Start an emulator or connect a device.
4. Run the `app` configuration from Android Studio.
5. Verify the configured public backend URL.

Agent and inference services are backend dependencies. Android does not configure their addresses.

## Tests

Unit tests:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

Debug APK:

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

Instrumented tests require a device:

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

## Mocking the Backend

Use MockWebServer and fixtures aligned with OpenAPI. Include:

- Successful responses
- Field-validation errors
- Expired authentication
- Forbidden resource
- Empty history/feed
- Recommendation fallback
- Chatbot source unavailable
- Network timeout

Mocks must not invent fields absent from the canonical contract.

## Debug Logging

Debug logging may include:

- Method
- Safe path
- Status
- Duration
- Correlation ID

It must exclude:

- Authorization header
- JWT content
- Passwords
- Dietary-sensitive values
- Chat messages
- Full Agent responses

Release builds must not enable verbose HTTP-body logging.

## Before Opening a Pull Request

- Run unit tests.
- Assemble the debug APK.
- Run relevant Compose UI tests.
- Verify an emulator happy path.
- Verify a failure/retry path.
- Confirm no generated, local, signing, or device file is staged.
- Confirm only the public backend URL is referenced.
- Record any required backend-contract version.

## Troubleshooting

### Emulator cannot reach backend

- Use `10.0.2.2`, not emulator `localhost`.
- Confirm the backend is listening on a reachable interface.
- Confirm cleartext HTTP is used only for an explicitly configured local build.
- Check host firewall settings.

### Gradle dependency metadata fails

- Check `compileSdk` against dependency requirements.
- Check the Android Gradle Plugin and Gradle compatibility.
- Refresh dependencies after aligning versions.

### Authentication works on Web but not Android

- Compare base URL and API version.
- Check bearer-token header formatting.
- Check device time.
- Compare the backend correlation ID and error code.
