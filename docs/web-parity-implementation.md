# Web-to-Android parity implementation

Android parity authority is the canonical 83-operation backend OpenAPI document at `foodmind-backend/src/main/resources/openapi/openapi.yaml`, shared with Web's locked contract snapshot.

## Feature mapping

| Web capability | Android destination | Backend/data boundary |
| --- | --- | --- |
| Login and register | `LoginActivity` | `/auth/login`, `/auth/register`, `/auth/refresh` |
| Recommendation form and result | `MainActivity`, `RecommendationDetailActivity` | `/recommendations/*`, groups, preferences, catalogue |
| Food/drink history and CRUD | `HistoryActivity`, `RecordCollectionActivity`, `RecordEditorActivity`, `RecordDetailActivity` | `/history`, `/food-records/*`, `/drink-records/*`, `/media/*` |
| Groups, invitations, members, feed, archive | `GroupsActivity`, `GroupWorkspaceActivity` | `/groups/*`, `/group-invitations/join` |
| Explore, search, content details | `ExploreActivity`, `CatalogueDetailActivity` | `/explore`, `/search`, `/catalogue/*` |
| Want to Try and recipes | `SavedActivity`, `RecipeLibraryActivity`, `RecipeEditorActivity` | `/want-to-try/*`, server-owned `/recipes/*` |
| Inventory | `InventoryActivity` | `/inventory/lots/*` list/filter/create/read/update/archive |
| Shopping lists | `ShoppingListsActivity`, `ShoppingListDetailActivity` | `/shopping-lists/*` list/filter/read/update/complete |
| Recipe import | `RecipeImportActivity`, `RecipeImportSessionActivity` | `/recipe-imports/*` submit/answer/confirm/status |
| Manual and recipe cooking | `ManualCookingActivity`, `RecipeLibraryActivity`, `CookingPlanDetailActivity` | `/cooking-plans/*`, catalogue preference codes |
| Chat sessions, messages, references | `ChatListActivity`, `ChatActivity` | `/chat/sessions/*`, `/search` |
| Dashboard and weekly recap | `DashboardActivity` | `/dashboard`, `/weekly-recaps/{weekStart}` |
| Profile and preferences | `ProfileActivity`, `ProfileEditorActivity`, `PreferencesActivity` | `/users/me`, `/users/me/preferences`, recommendation history |

## Contract synchronization

The backend owns recipe persistence and cooking accepts `recipeIds`. Android never substitutes local drafts for failed server requests. The root Gradle tasks provide executable synchronization gates:

- `apiGenerate` snapshots the sibling backend OpenAPI and generates reference Kotlin Retrofit APIs/models plus checked-in contract metadata;
- `apiCheck` verifies source hash, snapshot, generated DTO field manifest, and current backend source;
- `apiCoverage` requires exactly 83 unique Retrofit operations with no omissions or duplicates.

Release assembly additionally rejects a missing, non-HTTPS, or `.example` API origin.

## Session, media, and permission behavior

- Access tokens stay in memory; refresh tokens are AES/GCM-encrypted with an Android Keystore key.
- An OkHttp authenticator performs one bounded refresh after a 401 and retries the original request once.
- Record images use the system picker and the backend's create → object-storage PUT → finalise lifecycle.
- Explore, search, group feeds, catalogue detail, chat references, and Want to Try all use only authorised public API results.
- The application requests `INTERNET` only; it does not request broad photo, contacts, or location permissions.

## Verification

Local delivery gate:

```bash
./gradlew apiCheck clean testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin
```

MockWebServer covers bearer headers, refresh rotation/retry, correlation IDs,
idempotency headers, public endpoint paths, and optimistic-concurrency headers.
Release acceptance also installs the generated APK on an emulator and repeats the shared Web UAT scenario against the real backend and intelligence stack without API interception.
