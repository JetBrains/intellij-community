## WebView UI Authoring

- For WebView UI work, start with [WebView UI Authoring Guide](docs/guides/WebView-UI-Authoring-Guide.md).
- New UI code should load bundled assets through `createWebViewPanel(...)` and `WebViewAssetRoot.forView(viewId)`, not a local HTTP server.
- New bridge contracts should use typed `WebViewApi`, `WebViewApiId`, and `WebViewInterop` on the Kotlin side and `@jetbrains/intellij-webview` (`apiId`, `webView.callable`, `webView.implement`) on the TypeScript side.
- Do not copy raw `WebViewMessageBus`, raw method string, or direct `window.__WVI__` usage into new feature code unless the task is explicitly about the low-level runtime.
- For browser-only preview or smoke tests, use `@jetbrains/intellij-webview-testkit`; do not add mock-mode branches to production view code.
- Put WebView mocks under `webview-src/test/<view-id>/mocks`, keep browser smoke tests under `webview-src/test/<view-id>`, and do not put mocks into `resources/webview`.
- The standard preview command shape is `bun webview-preview <view-id> --mock <mock-name>` from the owning `webview-src` package. The demo reference is `bun webview-preview acp-chat --mock default` in `community/plugins/ui.webview/demo/webview-src`.
- Testkit packages are private workspace packages. Prefer local `file:` dependencies, `tsconfig` path mappings, and optional peers for `@jetbrains/intellij-webview`; do not make Bun resolve JetBrains private WebView packages from npm.

## WebView Local Code Style

- In WebView Kotlin, Java, and native bridge code, mark string literals that embed HTML or JavaScript code.
- Apply the same rule to related WebView native bridge files under `community/plugins/ui.webview/native/LinuxWebKitGtkBridge` and `community/plugins/ui.webview/native/WinWebView2Bridge`.
- Prefer `@Language("HTML")` or `@Language("JavaScript")` on a parameter, property, local variable, or helper function when the language applies to the whole value.
- If an annotation cannot be attached cleanly, put an IntelliLang marker immediately before the literal: `/*language=HTML*/` or `/*language=JavaScript*/`.
