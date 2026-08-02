# Web-to-Android parity implementation

Android parity authority:

- Web route inventory: `foodmind-web/src/app/router/router.tsx` at web `master` (`af643ca`).
- Backend contract: `foodmind-backend/src/main/resources/openapi/openapi.yaml` at backend `master` (`7ea2b90`).
- Android implementation branch: `feature/android-web-parity-xhs`.

## Feature mapping

| Web capability | Android destination | Backend/data boundary |
| --- | --- | --- |
| Login and register | `LoginActivity` | `/auth/login`, `/auth/register`, `/auth/refresh` |
| Recommendation form and result | `MainActivity`, `RecommendationDetailActivity` | `/recommendations/*`, groups, preferences, catalogue |
| Food/drink history and CRUD | `HistoryActivity`, `RecordCollectionActivity`, `RecordEditorActivity`, `RecordDetailActivity` | `/history`, `/food-records/*`, `/drink-records/*`, `/media/*` |
| Groups, invitations, members, feed, archive | `GroupsActivity`, `GroupWorkspaceActivity` | `/groups/*`, `/group-invitations/join` |
| Explore, search, content details | `ExploreActivity`, `CatalogueDetailActivity` | `/explore`, `/search`, `/catalogue/*` |
| Want to Try and recipes | `SavedActivity`, `RecipeLibraryActivity`, `RecipeEditorActivity` | `/want-to-try/*`; account-scoped local recipe drafts |
| Manual and recipe cooking | `ManualCookingActivity`, `RecipeLibraryActivity`, `CookingPlanDetailActivity` | `/cooking-plans/*`, catalogue preference codes |
| Chat sessions, messages, references | `ChatListActivity`, `ChatActivity` | `/chat/sessions/*`, `/search` |
| Dashboard and weekly recap | `DashboardActivity` | `/dashboard`, `/weekly-recaps/{weekStart}` |
| Profile and preferences | `ProfileActivity`, `ProfileEditorActivity`, `PreferencesActivity` | `/users/me`, `/users/me/preferences`, recommendation history |

## Contract decision: recipes

The current backend OpenAPI has no `/recipes` resource and
`GenerateCookingPlanRequest` has no `recipeIds` property. Android therefore:

1. stores recipe drafts locally in `SharedPreferences`, namespaced by authenticated user ID;
2. labels them as device-local in the UI;
3. scales the selected ingredient lines for target servings;
4. sends at most 30 supported `CookingIngredientRequest` objects to
   `POST /cooking-plans/generate`;
5. never reports a local draft as server-persisted.

## Session, media, and permission behavior

- Access tokens stay in memory; refresh tokens are AES/GCM-encrypted with an Android Keystore key.
- An OkHttp authenticator performs one bounded refresh after a 401 and retries the original request once.
- Record images use the system picker and the backend's create → object-storage PUT → finalise lifecycle.
- Explore, search, group feeds, catalogue detail, chat references, and Want to Try all use only authorised public API results.
- The application requests `INTERNET` only; it does not request broad photo, contacts, or location permissions.

## Verification

Local delivery gate:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin
```

MockWebServer covers bearer headers, refresh rotation/retry, correlation IDs,
idempotency headers, public endpoint paths, and optimistic-concurrency headers.
The local gate passes with 24 unit tests. On-device instrumentation remains a
separate gate because this workstation has no configured emulator or connected
Android device.
