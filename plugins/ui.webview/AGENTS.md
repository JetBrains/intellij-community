## WebView UI Authoring

- For WebView UI work, start with [WebView UI Authoring Guide](docs/guides/WebView-UI-Authoring-Guide.md).
- New UI code should load bundled assets through `createWebViewPanel(...)` and `WebViewAssetRoot.forView(viewId)`, not a local HTTP server.
- New bridge contracts should use typed `WebViewApi`, `WebViewApiId`, and `WebViewInterop` on the Kotlin side and `@jetbrains/intellij-webview` (`apiId`, `webView.callable`, `webView.implement`) on the TypeScript side.
- Do not copy raw `WebViewMessageBus`, raw method string, or direct `window.__WVI__` usage into new feature code unless the task is explicitly about the low-level runtime.
- For browser-only preview or smoke tests, use `@jetbrains/intellij-webview-testkit`; do not add mock-mode branches to production view code.
- Put WebView mocks under `webview-src/test/<view-id>/mocks`, keep browser smoke tests under `webview-src/test/<view-id>`, and do not put mocks into `resources/webview`.
- Prefer a runnable `webview-src/test/<view-id>/preview.ts` entry point using `@jetbrains/intellij-webview-testkit/node` for IDE Run UI. Keep `bun webview-preview <view-id> --mock <mock-name>` available for parameterized CLI runs. The demo references are `bun test/acp-chat/preview.ts` and `bun webview-preview acp-chat --mock default` in `community/plugins/ui.webview/demo/webview-src`.
- Add a `preview:<view-id>` package script, for example `"preview:acp-chat": "bun test/acp-chat/preview.ts"`, so the IDE can run the preview through Bun even when direct `.ts` Run defaults to Node.js.
- For direct IDE Run on `preview.ts`, the project JavaScript runtime must be Bun (`Settings | Languages & Frameworks | JavaScript Runtime | Preferred runtime: Bun`). If the IDE already created a Node.js run configuration for the file, delete or recreate it after switching the runtime.
- `runWebViewMockPreview(...)` must work regardless of the process working directory. If Vite reports that `views/<view-id>/index.html` is outside the serving allow list, fix the testkit Vite `server.fs.allow` roots, not production view code or mock code.
- Testkit packages are private workspace packages. Prefer local `file:` dependencies, `tsconfig` path mappings, and optional peers for `@jetbrains/intellij-webview`; do not make Bun resolve JetBrains private WebView packages from npm.
- After changing testkit files that are consumed through local `file:` dependencies, run `bun install` in the consuming `webview-src` package before validating IDE or `node_modules`-based behavior. Do not commit `node_modules`.
- Import `@jetbrains/intellij-webview-testkit/node` only from runnable preview scripts or Node-side tests. Browser mocks should import `defineWebViewMock` from `@jetbrains/intellij-webview-testkit`; production view code should import only `@jetbrains/intellij-webview`.
- For meaningful WebView UI flows, add or update a Playwright smoke test next to the mock. Start the preview with `startWebViewMockPreview(...)`, drive the page with user-level locators, assert rendered state, and use `window.__WVI_MOCK__.calls` only for bridge-contract assertions.
- When a view has multiple useful mock states, prefer separate runnable files such as `preview.default.ts`, `preview.empty.ts`, or `preview.error.ts`. Keep the CLI for parameterized local runs.

## WebView Local Code Style

- In WebView Kotlin, Java, and native bridge code, mark string literals that embed HTML or JavaScript code.
- Apply the same rule to related WebView native bridge files under `community/plugins/ui.webview/native/LinuxWebKitGtkBridge` and `community/plugins/ui.webview/native/WinWebView2Bridge`.
- Prefer `@Language("HTML")` or `@Language("JavaScript")` on a parameter, property, local variable, or helper function when the language applies to the whole value.
- If an annotation cannot be attached cleanly, put an IntelliLang marker immediately before the literal: `/*language=HTML*/` or `/*language=JavaScript*/`.
