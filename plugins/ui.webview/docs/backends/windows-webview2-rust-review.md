# WinWebView2Bridge — Rust Code Review

### Fix status

Most review findings are still open. Stage 1 of the [off-EDT plan](windows-webview2-off-edt-plan.md) added panic hardening around the message pump but did not address the Critical/High items in this review.

| ID | Issue | Status |
|---|---|---|
| C1 | Memory leak on the failure path of `create_native` (`lib.rs:630-634`) | ⬜ unfixed |
| C2 | `RefCell` borrow held across JNI in `attach_ipc_handler` / `attach_accelerator_key_handler` (`lib.rs:855-913`) | ⬜ unfixed; correct pattern already exists in `handle_web_resource_requested` |
| C3 | `js_string_literal` does not escape control characters U+0000..U+001F (`lib.rs:1193-1211`) | ⬜ unfixed |
| H1 | Thread-safety contract not expressed in the Rust crate (`lib.rs:35`, `:1070-1075`) | ⬜ unfixed; closes naturally with off-EDT Stage 3 (`assert_owning_thread`) |
| H2 | `web_message_token` not removed in `NativeWebView::destroy` (`lib.rs:234-269`) | ⬜ unfixed |
| H3 | `attachToParentNative` always leaves the window hidden (`lib.rs:321-335`) | ⬜ unfixed (Kotlin side recovers, but contract is brittle) |
| M1..M6 | Medium hygiene items | ⬜ unfixed |
| L1..L8 | Low / nit items | ⬜ unfixed |
| Panic hardening around the message pump | ✅ | shipped with off-EDT Stage 1 (`wvi-dedicated-thread-v2` ABI) |

## Overview

JNI bridge crate that backs the Windows WebView2 path of `intellij.platform.ui.webview`. Source lives in `community/platform/ui.webview/native/WinWebView2Bridge/src/lib.rs` (~1200 lines, single file), built as a `cdylib`. The Java side is `WinWebView2Bridge.kt` (declarations) plus `WinWebViewEngine.kt` (orchestration, which currently funnels every JNI call through `runOnEdt`).

This document inventories issues found in the Rust crate. The proposed off-EDT refactor is in [windows-webview2-off-edt-plan.md](windows-webview2-off-edt-plan.md).

## Files reviewed

- `community/platform/ui.webview/native/WinWebView2Bridge/src/lib.rs` — single Rust source.
- `community/platform/ui.webview/native/WinWebView2Bridge/Cargo.toml` — `jni 0.21`, `webview2-com 0.38`, `windows 0.61`.
- `community/platform/ui.webview/src/com/intellij/ui/webview/impl/windows/WinWebView2Bridge.kt` — Kotlin JNI declarations and ABI sentinel.
- `community/platform/ui.webview/src/com/intellij/ui/webview/impl/windows/WinWebViewEngine.kt` — engine that orchestrates native calls on EDT.
- `community/platform/ui.webview/tests/testSrc/com/intellij/ui/webview/WindowsWebViewSmokeTest.kt` — smoke tests.

## Issues

### Critical

#### C1. Memory leak on the failure path of `create_native`

`src/lib.rs:630-634`:

```rust
let raw = Rc::into_raw(native.clone()) as jlong;   // refcount++
native.borrow_mut().handle = raw;
begin_create_environment(native.clone(), user_data_dir)?;  // ? before raw is returned
Ok(raw)
```

If `begin_create_environment` returns `Err`, `?` propagates the error before `raw` is handed back to Java. Java only sees the exception, but the raw pointer keeps a reference count of at least one, and `destroyNative` is never called. `NativeWebView`, the child HWND, and any registered WebView2 handlers leak permanently.

**Fix.** Before propagating the error, reclaim ownership via `Rc::from_raw(raw)` and let it drop. Alternatively, defer `Rc::into_raw` until the very end of the happy path.

#### C2. `RefCell` borrow held across JNI calls in two WebView2 callbacks

- `attach_ipc_handler` (`src/lib.rs:855-881`):

    ```rust
    if let Ok(view) = native.try_borrow() {
        view.callbacks.on_message(message);   // JNI call under immutable borrow
    }
    ```

- `attach_accelerator_key_handler` (`src/lib.rs:903-913`) — same shape.

A JNI `call_method` on EDT can return control to Kotlin, which may reentrantly call `evaluateJavaScriptNative` or `setBoundsNative`. Those internally try `borrow_mut()` (via the `try_borrow_mut` chain) and silently fail. The result is occasionally lost messages with no diagnostic — hard to reproduce.

**Fix.** Clone `Rc<JavaCallbacks>` before invoking, drop the borrow, then call. The pattern is already correct in `handle_web_resource_requested` at `src/lib.rs:822-826`.

#### C3. `js_string_literal` does not escape control characters U+0000..U+001F (other than `\t \n \r`)

`src/lib.rs:1193-1211`. The literal flows into `__deliver(<literal>)` and then the JS side calls `JSON.parse`. RFC 8259 forbids unescaped control characters inside JSON strings, so the parse fails — the JS engine itself accepts the raw string, so the error surfaces only inside `JSON.parse` of `__deliver`. The result is silent message loss instead of a clear error.

**Fix.** Add a match arm `c if (c as u32) < 0x20 => write!(result, "\\u{:04x}", c as u32)`.

### High

#### H1. Thread-safety contract is not expressed anywhere

`NativeHandle = Rc<RefCell<NativeWebView>>` (`src/lib.rs:35`) and the `Rc::from_raw → clone → into_raw` dance in `run_with_handle` (`src/lib.rs:1070-1075`) are only sound when every JNI call comes from a single OS thread. The contract is enforced solely by the Kotlin side via `runOnEdt`; nothing in the Rust crate hints at this. A regression in Kotlin → undefined behavior in Rust with no diagnostic.

**Options (any of):**

1. `debug_assert!` thread id at creation and inside `run_with_handle`.
2. Header comment "// SAFETY: all JNI entry points must be invoked on the same OS thread that called `createNative`".
3. Replace with `Arc<Mutex<>>`. The single-thread invariant remains; cost is negligible.

#### H2. `web_message_token` is not removed in `NativeWebView::destroy`

`src/lib.rs:234-269`. `accelerator_key_pressed_token` and `web_resource_requested_token` are removed; `web_message_token` is not. In practice `self.webview = None` follows immediately and WebView2 cleans up on its own, but the asymmetry is fragile and easy to mishandle in a future refactor.

**Fix.** Model `web_message_token` as `Option<EventRegistrationToken>` like the others and remove it symmetrically.

#### H3. `attachToParentNative` always leaves the window hidden, even when `view.visible == true`

`src/lib.rs:321-335`:

```rust
controller.SetIsVisible(false)?;
ShowWindow(view.hwnd, SW_HIDE);
SetParent(view.hwnd, Some(parent))?;
// no visibility restore
```

This relies on the caller calling `setVisible` afterwards. `WinWebViewEngine.applyAttachmentState` does so, so it works in practice — but the native function's contract is brittle.

**Fix.** Restore `view.visible` at the end of `attachToParentNative`, or document "caller must restore visibility".

### Medium

- **M1.** `fail_create` (`src/lib.rs:926-935`) takes the borrow twice — first `try_borrow()` to clone callbacks, then `try_borrow_mut()` for `destroy`. Use one `try_borrow_mut()` plus a snapshot of callbacks.
- **M2.** `#[allow(dead_code)]` on `JavaCallbacks::on_log` (`src/lib.rs:143`) is misleading — the function is used at line 787.
- **M3.** Errors from `handle_web_resource_requested` are dropped if `try_borrow` fails (`src/lib.rs:786-792`) — same shape as C2. Clone the `Rc<JavaCallbacks>` outside the closure and log unconditionally.
- **M4.** `format_windows_error` uses `{:?}`. `windows::core::Error: Display` produces a cleaner "HRESULT 0x80004005: ..." string, which is what users will see in logs (`src/lib.rs:1217-1219`).
- **M5.** `CoInitializeEx` without a matching `CoUninitialize` (`src/lib.rs:594-596`). Once-per-thread leak; benign for EDT lifetime, but document the intent or pair it.
- **M6.** `attach_to_parent_native` propagates errors from `controller.SetIsVisible(false)` while best-efforting `ShowWindow` / `SetParent` errors with `let _` (`src/lib.rs:325-333`). Either propagate everything or none for transition operations.

### Low / nits

- **L1.** `RegisterClassExW` is called on every `create_container_hwnd` (`src/lib.rs:974-976`); silently returns 0 from the second call onward. Wrap in `std::sync::Once`.
- **L2.** `GetModuleHandleW(...).unwrap_or_default()` (`src/lib.rs:966`) hides hInstance acquisition errors. Propagate with `?`.
- **L3.** `is_message_in_range` (`src/lib.rs:1147-1149`) → `(first..=last).contains(&message)`.
- **L4.** `take_pwstr` is used at `src/lib.rs:821` and `:870` but only imported via the `webview2_com::*` glob at `src/lib.rs:17`. Prefer an explicit import.
- **L5.** `on_log` levels (`2 → warn`, else → info) are magic numbers. Define `LOG_LEVEL_INFO=0`, `LOG_LEVEL_WARN=2` near the `MODIFIER_*` constants (`src/lib.rs:38-41`).
- **L6.** Execute-script handlers are stored in `Vec<(u64, ...)>`; removal by id is O(n). With typical concurrent script counts (<10) this does not matter, but `HashMap<u64, _>` is more idiomatic.
- **L7.** `transferToJsNative` uses an empty completion handler (`src/lib.rs:558-561`). If IPC delivery fails, no signal reaches Java. Add at least an `on_log` on `Err`.
- **L8.** No `Drop` impl on `NativeWebView`. If the `Rc` ever dies without an explicit `destroyNative`, handlers and the HWND leak. `impl Drop for NativeWebView { fn drop(&mut self) { self.destroy() } }` is cheap insurance.

## Strengths

- `destroy` is properly idempotent (the `destroyed` flag).
- WebView2 filter and handlers are unregistered correctly.
- `pump_pending_messages_limited` with a 2 ms time budget plus the "leave for AWT" whitelist is a careful solution to EDT cohabitation. The off-EDT plan removes most of this complexity.
- Raw-pointer ↔ `Rc` lifecycle is correct: `into_raw` once at creation, `from_raw` once at destroy, and `from_raw → clone → into_raw` in `run_with_handle` preserves the count.
- Asset channel via `WebResourceRequested` + `SHCreateMemStream` is idiomatic.
- ABI-version sentinel (`NATIVE_ABI_VERSION = "wvi-scoped-pump-v1"`) is verified by the Kotlin loader to guard against stale DLLs (`WinWebView2Bridge.kt:22`).

## Suggested order of fixes

1. **C1** (leak in `create_native`) — one-line fix, real leak.
2. **C2** (borrow held across JNI) — apply the `handle_web_resource_requested` pattern to two callbacks.
3. **C3** (control characters in `js_string_literal`) — extra match arm.
4. **H1** (thread-safety contract) — comment + `debug_assert!` thread id.
    - Note: the off-EDT refactor preserves this contract, just bound to the new `WebView2-Thread`. The assert hardens it either way.
5. **H2, H3, M1–M6** — straight down the list.
6. Low / nits — as bandwidth allows.

## Verification

- Build: `cd community/platform/ui.webview/native/WinWebView2Bridge && cargo build --target x86_64-pc-windows-msvc` on a Windows host. On macOS/Linux the `#![cfg(target_os = "windows")]` produces an empty crate, as expected.
- Smoke tests: `WindowsWebViewSmokeTest` covers the main paths — `evaluateJavaScript_returnsResult`, `webMessageReceived_reachesBus`, `swingTextField_acceptsTypingAfterWebViewFocus`, `facade_survives_host_detach_reattach`. Windows host, non-headless.
- For C3, add a payload containing a control character to `transferToJs` and assert that the in-page `JSON.parse` does not throw.
- For C1, simulate a `CreateCoreWebView2EnvironmentWithOptions` failure (e.g. corrupt the user-data directory) and confirm that repeated `createNative` invocations do not accumulate `NativeWebView` instances (Process Hacker / `tasklist /v`).

## Open questions

1. Should `transferToJsNative` surface execution errors to Java (via `on_log` or a new `onTransferError` callback)?
2. Switch the execute-script handler bookkeeping from `Vec<(u64, _)>` to `HashMap<u64, _>`?
