# FoodMind Android

FoodMind Android is the native mobile client for FoodMind. It must expose the same business capabilities, validation semantics, permission outcomes, and backend metrics as the Web client while using mobile-appropriate navigation and interaction patterns.

> **Current status:** Android project scaffold plus directory framework. The existing activity still uses the original XML/AppCompat template; the planned Jetpack Compose architecture and FoodMind features are not yet implemented.

## Responsibilities

The Android application is responsible for:

- Registration and login UI
- Secure client-side session handling
- Profile and preference screens
- Food and drink record workflows
- History and filters
- Trusted groups and group feed
- Want to Try
- Recommendation context and result cards
- Acceptance, rejection, re-recommendation, and later rating
- Cooking-plan input and output
- FoodMind Chatbot sessions and grounded references
- Dashboard charts and weekly recap
- Mobile accessibility, lifecycle handling, and offline-aware UX

The Android application is not responsible for:

- Authoritative domain or permission rules
- Recommendation filtering, UserCF, ItemCF, or Logistic Regression
- Agent routing
- Analytics calculations
- Direct database, Agent, or inference-service access

## Technology Direction

Target architecture:

- Kotlin
- Jetpack Compose
- Navigation Compose
- ViewModel
- StateFlow
- Coroutines
- Retrofit and OkHttp
- Vico charts

The repository currently contains AppCompat, ConstraintLayout, and XML-view dependencies from the generated template. Migration to Compose must be deliberate and tested rather than treated as already complete.

## System Boundary

```text
Android application
  → HTTPS Spring Boot /api/v1
  → backend-owned security, domain, persistence, Agent, and ML flows
```

The Android build must contain only the public backend base URL. It must not contain database credentials, AWS secrets, model-registry credentials, or private service tokens.

## Repository Structure

```text
foodmind-android/
├── .github/workflows/                    # CI workflows
├── docs/
│   ├── architecture/
│   └── operations/
└── app/src/
    ├── main/java/com/foodmind/foodmind_android/
    │   ├── app/                          # Application composition
    │   ├── core/
    │   │   ├── designsystem/
    │   │   ├── model/
    │   │   ├── navigation/
    │   │   ├── network/
    │   │   └── security/
    │   ├── data/
    │   │   ├── local/
    │   │   ├── remote/
    │   │   └── repository/
    │   ├── domain/
    │   │   ├── model/
    │   │   ├── repository/
    │   │   └── usecase/
    │   └── feature/
    │       ├── auth/
    │       ├── profile/
    │       ├── records/
    │       ├── groups/
    │       ├── recommendation/
    │       ├── cooking/
    │       ├── chat/
    │       └── analytics/
    ├── test/java/com/foodmind/foodmind_android/
    │   ├── core/
    │   ├── data/
    │   ├── domain/
    │   ├── feature/
    │   └── fixtures/
    └── androidTest/java/com/foodmind/foodmind_android/
        ├── e2e/
        └── fixtures/
```

## Layer Responsibilities

| Layer | Responsibility |
| --- | --- |
| `app` | Root composition, global providers, and application startup |
| `core` | Stable shared Android infrastructure and UI primitives |
| `data` | Remote/local data sources and repository implementations |
| `domain` | Client-side models, repository interfaces, and use-case coordination |
| `feature` | Screens, ViewModels, UI state, actions, and feature navigation |

Client-side use cases coordinate presentation behaviour; they must not reproduce backend business rules.

## State Model

Each feature should use an explicit state and event model:

```text
User action
  → ViewModel
  → repository/use case
  → backend
  → result mapping
  → StateFlow
  → Compose UI
```

UI state should represent:

- Initial
- Loading
- Content
- Empty
- Validation error
- Authentication failure
- Forbidden/unavailable
- Network failure
- Recommendation fallback

Avoid exposing Retrofit response types directly to Composables.

## API Contract

- Public API owner: `foodmind-backend`
- Base path: `/api/v1`
- Authentication: bearer JWT
- Schema source: committed OpenAPI specification
- Internal service endpoints: prohibited
- Timestamps: ISO 8601
- IDs: opaque

Android and Web should share UAT scenarios, not source code.

## Build Configuration

The public backend URL should be injected through build configuration for each environment:

- `local`
- `staging`
- `production-demo`

No secrets may be embedded in `BuildConfig`, resources, the manifest, or committed Gradle properties.

## Build Baseline

Before feature implementation, confirm that the committed compile SDK, Android Gradle Plugin, Gradle version, AndroidX dependencies, and team Android Studio version are compatible. The baseline unit-test and debug-assembly commands must pass on a clean checkout.

## Local Development

Common Windows commands:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
```

Use Android Studio for emulator and instrumented-test workflows. See [local development](docs/operations/local-development.md).

## Security Rules

- Add only the network permission required for the public API.
- Use HTTPS outside local development.
- Do not log JWTs, request headers, dietary data, or Chatbot content.
- Treat local route guards as UX only; backend permission checks remain authoritative.
- Use secure platform storage for session credentials when implemented.
- Clear protected cached data on logout.
- Do not allow screenshots or backups to expose sensitive information without an explicit decision.

## Testing Strategy

- Unit tests for ViewModels, reducers, mappers, and use cases
- Repository tests with MockWebServer
- Compose UI tests for loading, content, error, and accessibility states
- Navigation tests for protected routes
- Instrumented tests for critical end-to-end paths
- Shared UAT scenarios for UC-01 through UC-09
- Contract fixtures matching the backend OpenAPI version

## Contribution Workflow

1. Link the feature to an Issue and acceptance criteria.
2. Confirm the backend contract version.
3. Work inside the owning feature and supporting layer.
4. Add unit/UI tests.
5. Run unit tests and assemble the debug APK.
6. Ensure no local SDK path, credentials, or device files are staged.
7. Open a reviewed Pull Request.

## Further Reading

- [Android architecture](docs/architecture/android-architecture.md)
- [Local development](docs/operations/local-development.md)
