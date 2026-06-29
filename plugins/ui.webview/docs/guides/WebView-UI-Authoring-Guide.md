# WebView UI Authoring Guide

Use this guide when adding an IntelliJ UI implemented as a local WebView page. The deeper design docs in this directory explain why the framework works this way; they are background reading, not prerequisites for ordinary UI work.

## Framework Shape

`intellij.platform.ui.webview` is an embedded WebView UI runtime for Swing-hosted IDE surfaces. Kotlin creates and owns the WebView lifecycle, static frontend assets are served from IDE resources, and Kotlin/TypeScript communicate through JSON-RPC.

For new UI code, use this stack:

- Kotlin host: `createWebViewPanel(...)`, `WebViewPanelOptions`, `WebViewAssetRoot`, `WebViewAssetPath`.
- Kotlin bridge contracts: `WebViewApi`, `WebViewImplementable`, `WebViewCallable`, `WebViewApiId`, `WebViewInterop`.
- TypeScript runtime package: `@jetbrains/intellij-webview`, especially `apiId`, `webView.callable(...)`, and `webView.implement(...)`.
- Frontend layout: `webview-src/views/<view-id>` as source and `resources/webview/views/<view-id>` as static output.

Avoid new feature code that talks directly to `window.__WVI__`, raw method strings, or `WebViewMessageBus`. Those APIs are low-level runtime details and test/runtime escape hatches. Use them only when changing the bridge itself.

## Create A View

Put frontend sources under the owning module:

```text
my/module/
  webview-src/
    views/
      my-view/
        index.html
        src/main.ts
    package.json
    tsconfig.json
    vite.config.ts
  resources/
    webview/
      views/
        my-view/
          index.html
          view.js
          styles.css
          assets/
            <package-name>.js
```

Use typed WebView APIs directly from the shared package. The Vite helpers inject the common WebView runtime assets into HTML before application code:

```ts
import { apiId, webView, type WebViewCallable, type WebViewImplementable } from "@jetbrains/intellij-webview"
```

Do not add raw `/__webview/*.js` script tags to HTML, and do not import shared WebView runtime code through checkout-relative paths such as `../../../../community/plugins/ui.webview/...`. Configure `package.json`, `tsconfig.json`, and the bundler so the same package import works for typechecking and bundling.

For Vite-based views, prefer the shared helpers from `@jetbrains/intellij-webview/vite`:

```ts
import { dirname } from "node:path"
import { fileURLToPath } from "node:url"
import { build } from "vite"
import { defineWebViewViewConfigs, selectWebViewViewBuildEntries, withWebViewBuildWatch } from "@jetbrains/intellij-webview/vite"

const webviewSrcDir = dirname(fileURLToPath(import.meta.url))
const selectedViews = selectWebViewViewBuildEntries(["my-view"])

for (const config of defineWebViewViewConfigs({ webviewSrcDir, views: selectedViews.views })) {
  await build(withWebViewBuildWatch(config, selectedViews.watch))
}
```

The helper emits commit-friendly static files. The view entry is `view.js`, CSS is `styles.css`, and dependencies from `node_modules` are split into stable package-name chunks under `assets/`, for example `assets/react.js` or `assets/mermaid.js`. Fonts and other emitted assets also live under `assets/` with stable names. Generated `resources/webview/` files should be committed until the frontend build is integrated into the main build graph.

## Preview And Mock A View In A Browser

For fast UI iteration without starting Kotlin, Swing, the IDE, or a native WebView engine, use the browser mock testkit. Production view code still imports `@jetbrains/intellij-webview` and talks through `webView.callable(...)` / `webView.implement(...)`; mock mode is installed by the preview/test harness, not by feature code.

Put mocks under `webview-src/test/<view-id>/mocks`, for example:

```text
webview-src/
  views/my-view/...
  test/my-view/mocks/default.ts
```

For IDE Run UI, add a small runnable preview entry point next to the mock:

```ts
import { runWebViewMockPreview } from "@jetbrains/intellij-webview-testkit/node"

await runWebViewMockPreview({
  importMetaUrl: import.meta.url,
  viewId: "my-view",
  mock: "default",
  open: true,
})
```

Add a package script for the preview:

```json
{
  "scripts": {
    "preview:my-view": "bun test/my-view/preview.ts"
  }
}
```

Run `webview-src/test/<view-id>/preview.ts` from the IDE or run the package script. The package script is the most predictable IDE entry point because it forces Bun. Direct Run on a `.ts` file uses Bun only when `Settings | Languages & Frameworks | JavaScript Runtime | Preferred runtime` is set to `Bun`; if the IDE already generated a Node.js configuration, delete or recreate that configuration after changing the runtime.

For parameterized CLI runs from the owning `webview-src` package, keep using:

```shell
bun webview-preview my-view --mock default
```

Both entry points serve the view through Vite, replace `/__webview/wvi-bridge.js` with the test bridge, and load the selected mock. Use `defineWebViewMock(...)` from `@jetbrains/intellij-webview-testkit` to implement host APIs and to call page APIs registered by the view.

Mock previews also serve `IconSet` URLs under `/__ij-icons/...` from generated `resources/webview` output, with the same light/dark URL shape used in IDE WebView. If a view uses view-local icons, keep the source assets under `webview-src/views/<view-id>` and make the build emit them into `resources/webview/views/<view-id>/assets`; pass the resulting classpath resource path such as `webview/views/<view-id>/assets/myIcon.svg` to `IconSet.src(...)`.

`runWebViewMockPreview(...)` infers `webviewSrcDir` from `importMetaUrl`, resolves mock names to `test/<view-id>/mocks/<mock>.ts`, and keeps Vite serving independent of the current working directory. If Vite reports that `views/<view-id>/index.html` is outside `server.fs.allow`, treat that as a testkit configuration bug; do not add mock-mode branches to the production view.

Keep mocks and browser smoke tests out of `resources/webview/`; that directory is generated production output. See [Frontend Testability Without IDE](../frontend/WebView-Frontend-Testability.md) for API details and the `demo/webview-src/test/acp-chat` reference mock.

## Test A View With Playwright

Use Playwright for browser smoke tests that need a real browser but not the IDE. The test should start a mock preview server, open `preview.url`, interact with the UI, and assert both rendered state and important bridge calls.

Put Playwright tests next to the mock:

```text
webview-src/
  test/my-view/
    mocks/default.ts
    my-view.browser.test.ts
```

Add a browser-test script in the owning `webview-src/package.json` when the package has Playwright tests:

```json
{
  "scripts": {
    "test:browser": "playwright test"
  },
  "devDependencies": {
    "@playwright/test": "^1.56.0"
  }
}
```

The test shape is:

```text
import { test, expect } from "@playwright/test"
import { startWebViewMockPreview } from "@jetbrains/intellij-webview-testkit"

let preview

test.beforeAll(async () => {
  preview = await startWebViewMockPreview({
    webviewSrcDir,
    viewId: "my-view",
    mock: mockFile,
  })
})

test.afterAll(async () => {
  await preview?.close()
})

test("runs my view in a browser", async ({ page }) => {
  await page.goto(preview.url)
  await page.getByRole("button", { name: "Run" }).click()
  await expect(page.getByText("Done")).toBeVisible()

  const calls = await page.evaluate(() => window.__WVI_MOCK__?.calls.byMethod("my.api/run") ?? [])
  expect(calls.length).toBeGreaterThan(0)
})
```

Prefer user-level locators (`getByRole`, `getByLabel`, `getByPlaceholder`) for interaction assertions and use `window.__WVI_MOCK__.calls` only for bridge-contract assertions. Do not assert implementation details that would make harmless markup changes break the smoke test. Run the test from the owning `webview-src` package:

```shell
bun run test:browser
```

## Host The View From Kotlin

Create the Swing-hosted WebView through `createWebViewPanel(...)`. This creates the engine-neutral WebView, host component, message bus, platform bridge, and initial asset load.

```kotlin
private val assetRoot = WebViewAssetRoot.forView("my-view")

private suspend fun createPanel(scope: CoroutineScope): WebViewPanel {
  return createWebViewPanel(
    scope = scope,
    options = WebViewPanelOptions(
      assetRoot = assetRoot,
      debugName = "My WebView panel",
    ),
  )
}
```

Call `createWebViewPanel(...)` from EDT and add `panel.component` to the Swing hierarchy on EDT. The `CoroutineScope` passed to `createWebViewPanel(...)` owns the WebView lifetime, so feature code should not call `WebViewPanel.close()` or `WebView.close()` as a parallel cleanup path. End the owner scope instead. Keep domain state, services, Swing objects, threading rules, and validation authority on the Kotlin side.

Kotlin hosting rules:

- Treat the returned `WebViewPanel` as the feature entry point. Do not introduce wrapper objects such as `WebViewResources` to carry the panel, page API proxies, and registrations side by side.
- Register host APIs through the entry point: `panel.interop.implement(...)`.
- Call page APIs through the same entry point: `panel.interop.callable(...)`. Prefer resolving callable proxies at the use site instead of caching a separate `pageApi` field next to the panel.
- Do not manually close a `WebViewPanel` that was created with the feature scope. The WebView runtime is scope-owned; feature cleanup should cancel or complete the corresponding scope.
- Avoid manual Swing/WebView focus hacks in ordinary content update paths. Focus behavior should be an explicit part of the UI contract or use an appropriate platform/WebView API, not ad-hoc AWT focus inspection and clearing.
- Scope listeners, asset providers, and bridge-related subscriptions to the same feature/viewer/editor lifetime. If an older API still requires a `Disposable`, derive it from the relevant scope with `scope.asDisposable()` and let the scope own it.

## Define Typed Protocols

Every cross-boundary contract should have matching Kotlin and TypeScript protocol declarations with the same namespace, method names, and JSON DTO shapes.

Kotlin implements APIs called from the page:

```kotlin
@Serializable
data class OpenFileRequest(val path: String, val line: Int? = null)

@Serializable
data class OpenFileResult(val opened: Boolean)

interface EditorHostApi : WebViewImplementable {
  companion object {
    val id: WebViewApiId<EditorHostApi> = WebViewApiId.of("editor.host")
  }

  suspend fun openFile(params: OpenFileRequest): OpenFileResult
}

panel.interop.implement(EditorHostApi.id, editorHostApi)
```

TypeScript calls that Kotlin API through a callable proxy:

```ts
type OpenFileRequest = { path: string; line?: number }
type OpenFileResult = { opened: boolean }

interface EditorHostApi extends WebViewCallable {
  openFile(params: OpenFileRequest): Promise<OpenFileResult>
}

const editorHostApiId = apiId<EditorHostApi>()("editor.host")
const hostApi = webView.callable(editorHostApiId)
declare const request: OpenFileRequest

async function openMainFile(): Promise<void> {
  await hostApi.openFile(request)
}
```

For notifications from Kotlin to the page, invert the roles: Kotlin declares `WebViewCallable`, TypeScript declares `WebViewImplementable`, Kotlin gets a proxy through `panel.interop.callable(...)`, and TypeScript registers handlers with `webView.implement(...)`.

Protocol rules:

- Declare namespace strings once through `WebViewApiId.of(...)` and `apiId(...)`.
- Wire method names are `namespace/methodName`; do not duplicate raw strings at call sites.
- Kotlin request/response methods are `suspend`; TypeScript request/response methods return `Promise`.
- Notification methods return `Unit` in Kotlin and `void` in TypeScript.
- Do not overload protocol methods.
- DTOs must be serializable boundary values: primitives, lists, maps, IDs, and nested DTOs.

## Frontend State Pattern

Treat the WebView page as a separate browser runtime. It should not see Kotlin services, mutable domain entities, `Flow`, `StateFlow`, Swing components, or IDE objects.

Recommended flow:

```text
Kotlin domain state -> serializable DTOs -> frontend store -> pure view models -> UI components
```

Keep frontend stores and projections plain. Components should subscribe to store state, call typed protocol functions, and render UI. Do not hide RPC calls inside projection functions or view-model getters.

## Browser-Like Behavior

Treat embedded WebView pages as application UI, not browser tabs. Feature pages should not depend on browser chrome behavior such as default context menus, page zoom, browser accelerator keys, status UI, autofill prompts, password-save prompts, swipe navigation, or elastic overscroll effects. Platform backends may disable those behaviors by default.

DevTools are host-controlled. Do not rely on browser shortcuts or default context-menu entries to open them; use an explicit IDE/debug action backed by the platform WebView implementation.

Browser/page zoom is not the right mechanism for feature UI. If a graph, canvas, diagram, image preview, or similar surface needs zoom, implement that as local component state: handle the relevant wheel or pointer gesture inside the component and update the component viewport, scale, transform, or renderer state. Do not add global `window`-level gesture handlers to guess which element should zoom.

Use normal scrolling for pages and panels. Add local zoom controls such as reset, fit, or explicit zoom buttons when the target user needs them; those controls should update the component's own model rather than the WebView page scale.

## Samples And Deeper Docs

Use `demo/` for the current sample layout, asset build, and `createWebViewPanel(...)` hosting pattern. Some demo bridge code still uses `WebViewMessageBus` and notification descriptors directly; do not copy that part for new feature protocols. Prefer the typed `WebViewApi` pattern described above.

Useful follow-up docs:

- [Frontend Build Strategy](../frontend/WebView-Frontend-Build-Strategy.md) for frontend source/output layout and resolver policy.
- [TypeScript RPC API Design](../architecture/WebView-TS-RPC-API-Design.md) for full typed bridge rules and examples.
- [Frontend View Model Patterns](../frontend/WebView-Frontend-View-Model-Patterns.md) for DTO/store/projection boundaries.
- [Frontend Framework Policy](../frontend/WebView-Frontend-Framework-Policy.md) for framework choices.
- [Design Docs](../directory.md) for the full document index.
