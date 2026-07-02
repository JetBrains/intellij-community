# WebView Platform — Plan & Index

> Status legend: ✅ done · ⏳ in progress · ⬜ todo · 🚫 blocked · 🗑️ dropped · 📊 analysis-only.
>
> This is the single entry point for `community/plugins/ui.webview/` docs. Open the linked sub-plans for full detail.

## 1. Quick start

- Add a WebView UI to a feature: [guides/WebView-UI-Authoring-Guide](guides/WebView-UI-Authoring-Guide.md)
- Understand the runtime (engines, providers, asset loading): [architecture/WebView-Runtime-Architecture](architecture/WebView-Runtime-Architecture.md)
- Configure browser console forwarding to IDE loggers: [guides/WebView-UI-Authoring-Guide § Use Browser Console Logging](guides/WebView-UI-Authoring-Guide.md#use-browser-console-logging)
- Review the API surface and the 13-item cleanup state: [architecture/WebView-Architecture-Review](architecture/WebView-Architecture-Review.md)
- Design a new JSON-RPC contract: [architecture/WebView-JsonRpc-Design](architecture/WebView-JsonRpc-Design.md) + [architecture/WebView-TS-RPC-API-Design](architecture/WebView-TS-RPC-API-Design.md)
- Preview or browser-test a WebView UI without IDE/Kotlin: [guides/WebView-UI-Authoring-Guide](guides/WebView-UI-Authoring-Guide.md) and [frontend/WebView-Frontend-Testability](frontend/WebView-Frontend-Testability.md)
- Know what's pending vs done: § 3 Roadmap (this doc)
- Understand why an old approach was changed: [historical/historical-decisions](historical/historical-decisions.md)

## 2. Decision trees

### 2.1 Which engine kind will the runtime pick?

```
WebViewEnginePreference / WebViewEngineRequirements
        │
        ▼
  WebViewRuntime selects a registered WebViewEngineProvider
  by capability match + availability
        │
        ├── macOS host? ───── SYSTEM_MACOS (WKWebView)
        │                     fallback → JCEF
        │
        ├── Windows host? ─── SYSTEM_WINDOWS (WebView2)
        │                     fallback → JCEF
        │
        └── Linux host?  ──── JCEF (system Linux backend not enabled)
```

Registry override: `ide.webview.engine` = `SYSTEM | JCEF`. Forced values are strict — unsupported engines fail with diagnostics, they do not silently downgrade. See [architecture/WebView-Runtime-Architecture § Engine Selection](architecture/WebView-Runtime-Architecture.md#engine-selection).

### 2.2 Where does my new code go?

```
                ┌─ all WebView pages need it? ─── YES ── wvi-platform-features.js
                │                                        (common runtime, see
                │                                         Common Runtime Injection)
                │
new mechanism ──┼─ one feature only? ─────────────── app bundle through
                │                                    defineWebViewViewConfig
                │
                ├─ engine/wiring code? ──────────── impl/engine/*
                │
                └─ native bridge? ──────────────── impl/<os>/ + community/native/<crate>
```

Rule of thumb: anything a feature author should NOT have to remember to install (focus, theme, browser console capture, IME hooks, accessibility bootstrap) goes into common runtime. See [architecture/WebView-Common-Runtime-Injection-Proposal](architecture/WebView-Common-Runtime-Injection-Proposal.md).

### 2.3 Level-1 bus or Level-2 interop?

```
I want to talk to the page…
        │
        ├── typed Kotlin interface with DTOs ──── Level 2 (WebViewInterop)
        │   (page implements / host implements    interop.callable(id) / .implement(id, impl)
        │    via reflection)
        │
        ├── one-way notification, no return ──── Level 1 (WebViewMessageBus)
        │                                        messageBus.notify(notification, params)
        │
        └── request/response without typed API ──── ⚠ P0 gap
                                                    (Architecture Review #9 — not in public API yet;
                                                    internal dispatcher supports it,
                                                    only public typed wrapper is missing)
```

`WebViewInterop.messageBus` is a deliberate escape hatch from Level 2 → Level 1; not a leak.

### 2.4 Should I write a new plan doc?

```
What's the size of the change?
        │
        ├── Wide API change / new subsystem / multi-week effort
        │   → yes, add a new plan doc in the right subfolder
        │     (interop / frontend / backends / strategy / architecture)
        │     and link it from § 3 Roadmap with a status icon.
        │
        └── Narrow code change (bug fix, small refactor, one TODO)
            → no plan doc. Edit the relevant section of an existing plan
              or just write the commit message.
```

Use the same status legend everywhere; don't invent new icons.

## 3. Roadmap

> Re-check status at every cleanup commit. If a doc says ✅ and the code says otherwise, fix the doc.

### P0 — Blocking / next-up
- ⬜ **Expose typed `call` / `registerCallHandler` in public `WebViewMessageBus`** — [architecture/WebView-Architecture-Review #9](architecture/WebView-Architecture-Review.md#recommendations-status). Internal dispatcher already routes call/response.
- ⬜ Rust review C1/C2/C3 fixes — [backends/windows-webview2-rust-review § Fix status](backends/windows-webview2-rust-review.md#fix-status)
- ⬜ Common Runtime Injection: build-helper contract tests + resolver contract tests + repo guard + artifact guard — [architecture/WebView-Common-Runtime-Injection-Proposal](architecture/WebView-Common-Runtime-Injection-Proposal.md)

### P1 — High (current milestone)
- ⬜ Windows WebView2 off-EDT Stage 2 (`AttachThreadInput`) — [backends/windows-webview2-off-edt-plan](backends/windows-webview2-off-edt-plan.md#stage-status)
- ⬜ Windows WebView2 off-EDT Stage 3 (`assert_owning_thread`) — closes rust-review H1
- ✅ Windows WebView2 application-mode defaults — disable browser-like UI behavior by default; [backends/windows-webview2-application-mode-plan](backends/windows-webview2-application-mode-plan.md)
- ✅ macOS WKWebView application-mode defaults — disable browser-like UI behavior by default; [backends/macos-wkwebview-application-mode-plan](backends/macos-wkwebview-application-mode-plan.md)
- ⬜ Architecture Review cleanup #1–7 (pure cleanup, no public API break)
- ⬜ Architecture Review cleanup #8 (`WebViewMessageRegistration` → `AutoCloseable`)
- ⬜ Architecture Review cleanup #10–13 (engine type unification, first-class `WebViewTransport`, `WebViewMessageContext` decision)
- ⏳ Focus interop Stage 7 — full diagnostics logging events listed in plan — [interop/WebView-Focus-Tab-Interop-Plan](interop/WebView-Focus-Tab-Interop-Plan.md)
- ✅ Native WebView heavyweight overlay facade for balloons/notifications — [interop/WebView-Heavyweight-Component-Registry-Plan](interop/WebView-Heavyweight-Component-Registry-Plan.md)
- ⬜ Bridge-Ready explicit `$/webview/bridgeReady` signal in TS runtime + message-bus-owned readiness gating + `configure` overload — [interop/WebView-Bridge-Ready-Panel-Plan](interop/WebView-Bridge-Ready-Panel-Plan.md)

### P2 — Medium (planned)
- ⬜ DnD Interop v1 (macOS WKWebView + Windows WebView2) — [interop/WebView-Drag-And-Drop-Interop-Plan](interop/WebView-Drag-And-Drop-Interop-Plan.md)
- ⏳ Frontend Testability harness — Browser mock testkit V1 exists for TypeScript/Vite previews; Java backend layer deferred — [frontend/WebView-Frontend-Testability](frontend/WebView-Frontend-Testability.md)
- ⬜ WebView IconSet loading for classloader-backed IntelliJ icons — [frontend/WebView-IconSet-Loading-Plan](frontend/WebView-IconSet-Loading-Plan.md)
- ⬜ Frontend SDK Distribution (versioned npm + SDK tarball + compatibility check) — [frontend/WebView-Frontend-SDK-Distribution](frontend/WebView-Frontend-SDK-Distribution.md)
- ⬜ Control Parity scaffold (`@jetbrains/intellij-webview-controls`) — [frontend/WebView-Control-Parity-Design](frontend/WebView-Control-Parity-Design.md)
- ⬜ Bazel `webview_assets` rule (replace manual build) — [frontend/WebView-Frontend-Build-Strategy](frontend/WebView-Frontend-Build-Strategy.md)

### P3 — Strategic / long-term
- 🚫 Markdown WebView preview source-bound API reshape — blocked on approval for broad Markdown preview API changes; [interop/Markdown-WebView-Preview-API-Plan](interop/Markdown-WebView-Preview-API-Plan.md)
- 🚫 Linux WebKitGTK interactive input + asset serving — blocked on Wayland child-surface design or equivalent JBR API; [backends/linux-webkitgtk-runtime](backends/linux-webkitgtk-runtime.md)
- ⬜ Junie Remote UI integration (Approach 6 — trusted loopback Junie app with injected IntelliJ runtime) — [strategy/Junie-Remote-UI-Integration-Options](strategy/Junie-Remote-UI-Integration-Options.md)
- ⬜ Plugin extraction — [strategy/WebView-Plugin-Extraction-Feasibility](strategy/WebView-Plugin-Extraction-Feasibility.md) (analysis-only; needs platform reshaping in 2026.2+)
- ⬜ Marketplace autopublish wiring — [strategy/WebView-Marketplace-Autopublish-Plan](strategy/WebView-Marketplace-Autopublish-Plan.md)

### Deferred / not on near-term roadmap
- Performance gates and UX acceptance criteria beyond current smoke coverage.
- Accessibility traversal parity (Focus Interop v2 — dedicated a11y pass).
- Internal Remote Dev engine proxy forwarding `LoadAsset` / `LoadHtml` / `EvaluateJavaScript` / `TransferToJs` / `Close`.
- Rust review M1–M6 and L1–L8 hygiene items.

## 4. Documents by folder

### `guides/` — How-to & conventions
- [WebView UI Authoring Guide](guides/WebView-UI-Authoring-Guide.md) — start here for new UIs, browser mock previews, and Playwright smoke tests.
- [Coding Guides](guides/Coding-Guides.md) — Kotlin conventions + Threading Model section (EDT vs macOS main thread).
- [Kotlin Reactive Stream Ownership Guideline](guides/kotlin-reactive-stream-ownership-guideline.md)
- [Pre-refactoring Tests](guides/pre_refactoring_tests.md) — refactoring safety net.

### `architecture/` — Runtime, public API, RPC
- [WebView Runtime Architecture](architecture/WebView-Runtime-Architecture.md) — engine selection, providers, asset loading, message bus contract.
- [WebView Architecture Review](architecture/WebView-Architecture-Review.md) — type surface review + 13-item ROI cleanup with status table.
- [WebView JSON-RPC Design](architecture/WebView-JsonRpc-Design.md) — current Kotlin RPC spec.
- [WebView TS RPC API Design](architecture/WebView-TS-RPC-API-Design.md) — current TypeScript RPC spec.
- [WebView Common Runtime Injection](architecture/WebView-Common-Runtime-Injection-Proposal.md) — ✅ implemented; outstanding test work tracked in the doc.

### `frontend/` — Frontend platform
- [Build Strategy](frontend/WebView-Frontend-Build-Strategy.md) — ⏳ Vite helper + monorepo aliases ship; Bazel + SDK pipeline deferred.
- [Dependency Resolution](frontend/WebView-Frontend-Dependency-Resolution.md) — resolver policy.
- [Framework Policy](frontend/WebView-Frontend-Framework-Policy.md) — Custom Elements, Lit, Preact/React/Svelte tradeoffs.
- [View Model Patterns](frontend/WebView-Frontend-View-Model-Patterns.md) — Kotlin/WebView state boundary, DTOs, stores, projections.
- [SDK Distribution](frontend/WebView-Frontend-SDK-Distribution.md) — ⬜ design only.
- [Testability Without IDE](frontend/WebView-Frontend-Testability.md) — ⏳ browser mock testkit V1 implemented; covers `@jetbrains/intellij-webview-testkit`, TS/Bun preview entry points, package scripts, IDE Bun runtime setup, and Playwright smoke tests.
- [IconSet Loading Plan](frontend/WebView-IconSet-Loading-Plan.md) — ⬜ design only.
- [Control Parity Design](frontend/WebView-Control-Parity-Design.md) — ⬜ design only.

### `interop/` — Swing ↔ WebView boundary
- [Focus & Tab Interop](interop/WebView-Focus-Tab-Interop-Plan.md) — ✅ implemented (stages 1–6, 8); Stage 7 diagnostics ⏳.
- [Heavyweight Overlay Interop](interop/WebView-Heavyweight-Component-Registry-Plan.md) — ✅ WebView-local `HwFacadeProvider` decorator for native-host balloons/notifications; broader popup/hint coverage deferred until a concrete broken path is confirmed.
- [Drag-and-Drop Interop](interop/WebView-Drag-And-Drop-Interop-Plan.md) — ⬜ design only.
- [Bridge-Ready Panel](interop/WebView-Bridge-Ready-Panel-Plan.md) — ⏳ JCEF internal handling ✅; platform-wide `$/webview/bridgeReady` ⬜.
- [Markdown WebView Preview API](interop/Markdown-WebView-Preview-API-Plan.md) — 🚫 blocked on approval for broad Markdown preview API changes.

### `backends/` — Native rendering / engine impls
- [Windows WebView2 Implementation](backends/windows-webview2-implementation-plan.md) — ✅ done.
- [Windows WebView2 Application Mode](backends/windows-webview2-application-mode-plan.md) — ✅ implemented.
- [macOS WKWebView Application Mode](backends/macos-wkwebview-application-mode-plan.md) — ✅ implemented.
- [Windows WebView2 Off-EDT](backends/windows-webview2-off-edt-plan.md) — Stage 1 ✅, Stages 2–3 ⬜.
- [Windows WebView2 Rust Bridge Review](backends/windows-webview2-rust-review.md) — issues catalogued with status table; C1/C2/C3/H1–H3 + M/L items mostly ⬜.
- [Linux WebKitGTK Runtime](backends/linux-webkitgtk-runtime.md) — ⏳ Wayland snapshot display-only; interactive input / asset serving / X11 ⬜.
- [JCEF Backend](backends/jcef-in-process-backend-plan.md) — ✅ done (JBCEF-owned rendering mode).

### `strategy/` — Distribution / scope
- [Plugin Extraction Feasibility](strategy/WebView-Plugin-Extraction-Feasibility.md) — 📊 analysis only.
- [Junie Remote UI Integration Options](strategy/Junie-Remote-UI-Integration-Options.md) — ⬜ design only (Approach 6 recommended).
- [Marketplace Autopublish Plan](strategy/WebView-Marketplace-Autopublish-Plan.md) — ⬜ build/release wiring plan for WebView runtime and Markdown WebView Preview.

### `historical/` — Archive
- [Historical Decisions Digest](historical/historical-decisions.md) — 1-file digest of approaches that were tried and changed; entry point for "why is X this way?" questions.
- [WebView Strategic Analysis](historical/WebView-Strategic-Analysis.md) — full strategic positioning analysis (RU); preserved as-is for traceability.

## 5. Conventions for updating this doc

- **Status icons stay machine-greppable.** If you change a doc's status, also update the matching line in § 3 Roadmap. `rg "⏳|⬜|🚫"` should agree with reality.
- **New plan = new entry.** Any new plan doc must be linked from § 3 Roadmap with a status icon, not just from § 4. § 4 is the index; § 3 is the action list.
- **Done plans stay as design rationale.** A ✅ plan doc is not deleted — it stays as the design record. If the doc becomes purely historical (approach changed), extract the rationale to `historical/historical-decisions.md` and delete the doc.
- **POC / pre-refactor docs go to `historical/`, not deleted in place.** The only time you can delete an active doc is when its content has fully migrated into a current doc and no audit-trail value remains.
- **Cross-folder links are relative.** Use `../<folder>/<file>.md` (e.g. from `frontend/` to `architecture/`). Never absolute paths.
