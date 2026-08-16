# Web-to-Android parity implementation

Android parity authority:

- Web route inventory: `foodmind-web/src/app/router/router.tsx` at web `master` (`af643ca`).
- Backend contract: `foodmind-backend/src/main/resources/openapi/openapi.yaml` at backend `master` (`7ea2b90`).
- Android implementation branch: `feature/android-web-parity-xhs`.

## Feature mapping

| Web capability | Android destination | Backend/data boundary |
| --- | --- | --- |
| Login and register | `LoginActivity` | `/auth/login`, `/auth/register`, `/auth/refresh` |
| Recommendation context and result | `MainActivity`, `RecommendationContextActivity`, `RecommendationDetailActivity` | `/recommendations/*`, groups, preferences, catalogue |
| Food/drink history and CRUD | `HistoryActivity`, `RecordCollectionActivity`, `RecordEditorActivity`, `RecordDetailActivity` | `/history`, `/food-records/*`, `/drink-records/*`, `/media/*` |
| Groups, invitations, members, feed, archive | `GroupsActivity`, `GroupWorkspaceActivity` | `/groups/*`, `/group-invitations/join` |
| Explore, search, content details | `ExploreActivity`, `CatalogueDetailActivity` | `/explore`, `/search`, `/catalogue/*` |
| Want to Try and recipes | `SavedActivity`, `RecipeLibraryActivity`, `CookingRecipeEditorActivity` | `/want-to-try/*`, `/recipes/*`, `/recipe-imports/*` |
| Cooking selection and preferences | `CookingHomeActivity`, `CookingSettingsActivity` | `/recipes`, `/cooking-plans/generate-async`, account dietary preferences plus on-device region |
| Cooking history and live task progress | `CookingPlansActivity`, `CookingPlanDetailActivity` | `/cooking-plans/history`, `/cooking-plans/{id}`, `/task`, `/cancel` |
| Shopping and real inventory | `ShoppingListsActivity`, `ShoppingListActivity`, `CookingInventoryActivity` | `/shopping-lists/*`, `/inventory/lots/*` |
| Manual cooking | `ManualCookingActivity`, `CookingPlanDetailActivity` | `/cooking-plans/*`, catalogue preference codes |
| Chat sessions, messages, references | `ChatListActivity`, `ChatActivity` | `/chat/sessions/*`, `/search` |
| Dashboard and weekly recap | `DashboardActivity` | `/dashboard`, `/weekly-recaps/{weekStart}` |
| Profile and preferences | `ProfileActivity`, `ProfileEditorActivity`, `PreferencesActivity` | `/users/me`, `/users/me/preferences`, recommendation history |

## Contract decision: recipes

The current backend OpenAPI exposes owner-scoped `/recipes` endpoints and
`GenerateCookingPlanRequest.recipeIds`. The Web-aligned Android cooking flow therefore:

1. reads recipe cards from `GET /recipes`;
2. imports multilingual pasted recipes through the Agent-backed `/recipe-imports` workflow;
3. reads, updates, and deletes account recipes through `/recipes/{id}`;
4. sends the selected recipe IDs to `POST /cooking-plans/generate-async` and opens the processing detail immediately;
5. polls task progress in the detail page, supports cancellation, and then renders the terminal plan;
6. lets the backend reload the recipes and current inventory before planning;
7. persists only the cooking region and local execution-board progress on-device; dietary and allergen constraints always come from account Preferences.

Legacy device-local drafts remain available to the older recipe-library flow, but are no
longer the authority for the Web-aligned Cooking selection page.

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
The local gate passes with the Android unit-test suite. A Pixel emulator regression also
covered the single Kitchen menu, live backend recipe/inventory/shopping-list reads, backend
recipe editing, immediate PROCESSING navigation, automatic READY transition, and execution
progress restoration after force-stopping and reopening the app.
