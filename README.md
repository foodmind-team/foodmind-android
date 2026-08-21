# FoodMind Android

FoodMind Android is the native mobile client for FoodMind. It provides the same public capabilities and Backend-owned validation semantics as the Web client through Kotlin, Jetpack Compose, Retrofit, and OkHttp.

## Live deployment

The deployed FoodMind Web application is available at [https://13.229.2.154.sslip.io/](https://13.229.2.154.sslip.io/). To build Android against the same HTTPS deployment for a physical device, use:

```bash
./gradlew --no-daemon assembleDebug \
  -Pfoodmind.debugApiBaseUrl=https://13.229.2.154.sslip.io/api/v1/
```

## Features

- Sign-in, profile, preferences, records, history, trusted groups, and Explore
- Recommendation decisions and feedback, cooking plans, shopping lists, recipes, and chat
- Backend-owned dashboards and weekly recaps
- Secure client session handling, lifecycle-aware state, and offline/error recovery

The app talks only to the public Backend `/api/v1` contract. It never connects directly to PostgreSQL, Agent services, or inference services.

## Prerequisites

- Android Studio with the SDK requested by the project
- JDK 17
- An Android emulator or physical device
- A FoodMind Backend reachable from that device

Do not commit `local.properties`, SDK paths, keystores, or device-specific settings.

## Quick start

```bash
git clone https://github.com/foodmind-team/foodmind-android.git
cd foodmind-android
./gradlew --no-daemon apiCheck testDebugUnitTest assembleDebug
```

Start the Backend first. A debug build defaults to the Android emulator route:

```text
http://10.0.2.2:8080/api/v1/
```

To use another host, pass an API URL with the required trailing slash:

```bash
./gradlew --no-daemon assembleDebug \
  -Pfoodmind.debugApiBaseUrl=http://10.0.2.2:8080/api/v1/
```

Then select the `app` configuration in Android Studio and run it on an emulator. For a physical device, use a reachable HTTPS staging URL instead of `10.0.2.2`.

## Local deployment

Start the full Backend stack from
[FoodMind Infrastructure](https://github.com/foodmind-team/foodmind-infra)
before launching Android. Wait for its readiness endpoint to return `UP`; the
Android app must call only the Backend `/api/v1` API and never a database,
storage service, Agent, or Inference service.

For an Android emulator, the default debug URL already maps the emulator's
`10.0.2.2` host alias to the Infra Backend on port `8080`:

```powershell
# In foodmind-infra:
docker compose up --build -d --wait
Invoke-RestMethod http://localhost:8080/actuator/health/readiness

# In foodmind-android:
.\gradlew.bat --no-daemon assembleDebug `
  -Pfoodmind.debugApiBaseUrl=http://10.0.2.2:8080/api/v1/
```

Run the `app` configuration from Android Studio or install the resulting debug
APK on the emulator. For a USB-connected physical device, do not use
`10.0.2.2`; forward the host Backend port and use the device loopback address:

```powershell
adb reverse tcp:8080 tcp:8080
.\gradlew.bat --no-daemon assembleDebug `
  -Pfoodmind.debugApiBaseUrl=http://127.0.0.1:8080/api/v1/
```

Alternatively, supply a device-reachable HTTPS Backend URL with the required
trailing `/api/v1/`. Keep signing keys, `local.properties`, service tokens, and
cloud credentials outside Git. Stop the shared runtime from the Infra checkout
with `docker compose down`; this retains local volumes unless `--volumes` is
explicitly requested.

## Build variants and API URLs

| Build | API URL source | Default |
| --- | --- | --- |
| Debug | `foodmind.debugApiBaseUrl` or `FOODMIND_API_BASE_URL` | Emulator route above |
| Release | `foodmind.apiBaseUrl` or `FOODMIND_API_BASE_URL` | HTTPS staging fallback |

Release builds reject missing, insecure, placeholder, or malformed URLs. The API base URL must end in `/api/v1/`.

## API configuration and credentials

Android accepts only the public Backend API base URL. Configure it temporarily
for a command or IDE launch; include the required trailing `/api/v1/`:

```powershell
# Debug emulator; Gradle property takes precedence over the environment variable.
.\gradlew.bat --no-daemon assembleDebug `
  -Pfoodmind.debugApiBaseUrl=http://10.0.2.2:8080/api/v1/

# Alternatively, supply a public Backend URL to Gradle.
$env:FOODMIND_API_BASE_URL='https://<approved-backend-host>/api/v1/'
.\gradlew.bat --no-daemon assembleDebug
```

Release builds require `FOODMIND_API_BASE_URL` to be HTTPS and reject
placeholder or malformed values. The app does not accept API keys, database
credentials, OneMap tokens, LLM keys, S3 secrets, or private Agent tokens.
User authentication is obtained through the public Backend flow and stored by
the app's session layer; do not hard-code a bearer token in Gradle, source, or
`local.properties`.

## Contract workflow

The checked-in [`contracts/backend-openapi-v1.yaml`](contracts/backend-openapi-v1.yaml) mirrors the Backend contract. With the sibling Backend checkout available, regenerate after an approved public API change:

```bash
./gradlew --no-daemon apiGenerate
./gradlew --no-daemon apiCheck
```

The generated reference client is not the application implementation; Retrofit declarations and contract metadata are checked for drift.

## Verify

```bash
./gradlew --no-daemon apiCheck clean testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin
./gradlew --no-daemon connectedDebugAndroidTest   # requires a device or emulator
```

## Repository layout

```text
app/src/main/        Compose UI, data, domain, networking, and application code
app/src/test/        Unit tests and API fixtures
app/src/androidTest/ Instrumented and end-to-end tests
contracts/           Versioned Backend OpenAPI snapshot and lock metadata
docs/                Architecture, development, UX, and operations guides
scripts/             Local validation helpers
```

## Contributing

Keep domain meaning, permissions, and validation aligned with the Backend and Web client. Add unit or UI coverage for a behaviour change, verify the happy and failure paths on a device when applicable, and run the relevant commands above before opening a pull request.

## Security

Only the public Backend base URL belongs in a build. Do not include cloud credentials, signing keys, database details, private service URLs, request bodies, or bearer tokens in source code or debug logs.

## License

No open-source license is currently included in this repository. Obtain permission from the maintainers before redistributing or reusing the code.
