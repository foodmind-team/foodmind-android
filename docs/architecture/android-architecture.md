# Android Architecture

## Goals

- Native Android experience using Compose.
- Recommendation-first home experience with Cooking available through the same top-level mode switch.
- Behavioural parity with FoodMind Web.
- Lifecycle-safe, observable state.
- Clear separation between presentation, domain coordination, and data access.
- Testable network and error handling.
- No duplication of backend business rules.

## Application Flow

```text
Composable
  → UI action
  → ViewModel
  → domain use case
  → repository interface
  → repository implementation
  → Retrofit service
  → Spring Boot /api/v1
```

Responses travel back through mapping layers into immutable UI state.

## Package Rules

### `app`

Owns:

- Application root
- Dependency composition
- Root navigation host
- App-wide lifecycle concerns

It should not contain feature screens or API DTOs.

### `core`

Owns stable shared facilities:

- Design tokens and common Compose primitives
- Navigation contracts
- HTTP-client configuration
- Shared client models
- Session abstractions

Move code into `core` only when it is genuinely shared.

### `data`

Owns:

- Retrofit declarations
- Remote DTOs
- Local cache/storage adapters
- Repository implementations
- Network-to-domain mapping

DTOs remain inside the data boundary.

### `domain`

Owns:

- Client-side domain models needed by multiple features
- Repository interfaces
- Use cases that coordinate client behaviour

It does not implement backend authorisation or recommendation algorithms.

### `feature`

Each feature owns:

- Screen-level Composables
- ViewModel
- Immutable UI state
- User actions/events
- Feature navigation destination
- Feature tests

Features should not import another feature's private implementation.

## Unidirectional Data Flow

Recommended pattern:

```text
UiState + one-off UiEffect
          ↑
       ViewModel
          ↑
       UiAction
          ↑
      Composable
```

Guidelines:

- Use `StateFlow` for durable screen state.
- Use a deliberate effect mechanism for transient navigation or messages.
- Do not start network requests directly from Composables.
- Make retry an explicit action.
- Preserve entered form data across recoverable failures.

## Navigation

Keep three independent AI entry points:

- Generate Food Recommendation
- Generate Cooking Plan
- FoodMind Chatbot

The Chatbot must not become a hidden navigation path to recommendation or cooking.

At the presentation layer, the first two entry points share a top-level mode
switch:

- **Eat out & delivery** is the default and shows the primary Generate Recommendation action.
- **Cooking** navigates directly to account-recipe selection and plans against current inventory with serving, time, region, and account dietary context.
- The Chatbot remains a separate destination and workflow.

Persistent labeled navigation contains Home, Groups, Explore, Saved, and Me.
Groups is a core shared workspace. Explore may show only group-visible records
and curated platform content that the backend authorises; it is not a public
social feed.

Navigation arguments should contain identifiers, not large serialised objects. Screens reload authorised data from the backend.

## Network Boundary

The shared HTTP client should provide:

- Public base URL
- JSON configuration
- Authentication interceptor
- Correlation-ID propagation
- Safe request/response logging for debug builds only
- Error-envelope parsing
- Timeouts appropriate for normal and AI-assisted requests

Do not send requests directly from UI code.

## Error Mapping

Map transport and backend errors into stable client failures:

- Validation
- Authentication expired
- Forbidden
- Not found/unavailable
- Network offline
- Server unavailable
- Agent unavailable with fallback
- Unexpected

Backend field errors must map back to form fields.

## Recommendation Presentation

UI state should retain:

- Session ID
- The ordered candidate set containing Personal, Exploratory, and Group-inspired types
- The currently spotlighted lead candidate
- Candidate identifiers
- Grounded explanation and reason codes
- Model version/status when supplied
- Fallback status
- Feedback submission state

Do not derive explanations from score values on-device.

The backend may return up to three intentionally different candidates. Android
initially displays one lead result and moves through the returned alternatives
with an explicit “try another” action; this does not silently create a new
session.

## Local Persistence

Add local storage only for confirmed client needs:

- Secure session material
- Explicitly approved lightweight cache
- Unsaved form state where useful

PostgreSQL through Spring Boot remains the source of truth. Do not create an independent offline business database during the MVP unless scope is explicitly changed.

## Cross-Client Parity

Android and Web share:

- OpenAPI contract
- Error codes
- Permission semantics
- Metric definitions
- UAT cases

Android may use bottom navigation, mobile sheets, and platform-specific interaction patterns without breaking parity.
