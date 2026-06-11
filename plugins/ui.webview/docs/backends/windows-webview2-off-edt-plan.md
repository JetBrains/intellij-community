# Plan: move WebView2 calls off EDT

> **Status (May 2026):** Stage 1 landed on branch `n500/262/light-webview-poc`. Native side bumped through `wvi-dedicated-thread-v2` (catch_unwind around posted tasks, `GetMessageW` error reporting, JNI exception bridge, `target_tid == 0` guard). Kotlin side schedules through `invokeOnWebView` (direct `dispatcher.dispatch`, not `scope.launch`) so `close()` cannot race with `scope.cancel()`. Hot-path operations (`setBounds`, `setHidden`, `requestFocus`/`clearFocus`, `attachToParent`, `loadUrl`/`loadHtml`) are coalesced via single-slot pending + `AtomicBoolean` scheduled flags. Stages 2 (`AttachThreadInput` focus interop) and 3 (`assert_owning_thread`) are not yet done.

### Stage status

| Stage | Status | Reference |
|---|---|---|
| 1. `WebView2Dispatcher` + native Win32 message loop, hot-path coalescing, panic hardening | ✅ | `impl/windows/WebView2Dispatcher.kt`, `WinWebView2Bridge/src/lib.rs` (ABI `wvi-dedicated-thread-v2`) |
| 2. `AttachThreadInput` / Win32 focus attachment from EDT to WebView2 thread | ⬜ | not started |
| 3. `assert_owning_thread()` debug check across native entry points | ⬜ | not started; ties to rust-review H1 |

Recommended ordering: H1 (thread-id `debug_assert!`) from the rust review can land first as a low-risk hygiene step and de-risks Stage 3. See [windows-webview2-rust-review.md](windows-webview2-rust-review.md) for details.

## Motivation

Today `WinWebViewEngine.runOnEdt` (`WinWebViewEngine.kt:437`) is `SwingUtilities.invokeLater` — the caller does not block. But **EDT is held for the duration of every WebView2 JNI call and every 16 ms pump tick**. Under load (heavy `ExecuteScript`, `Navigate` on a heavy page, WebMessage bursts) this introduces UI hitches in the IDE itself.

**Goal.** Move every WebView2 STA operation onto a dedicated `WebView2-Thread`, leaving EDT for AWT/Swing.

The Rust code-review issues (independent of this refactor) are documented separately in [windows-webview2-rust-review.md](windows-webview2-rust-review.md). This plan does not depend on those fixes; the C1/C2/C3 issues should still be fixed first as low-risk hygiene.

## Target architecture

```
+-------------+              +---------------------------+
|     EDT     |  invokeLater |   WebView2-Thread         |
|   (AWT)     |--?--?--?--?->|  Dispatchers.WebView2     |
| owns parent |              |  CoInitializeEx STA       |
|   HWND      |              |  owns child HWND          |
|             |<-------------|  pumps own message queue  |
+-------------+   callback   +---------------------------+
```

- `WebView2Dispatcher` — daemon thread named `WebView2-Thread` running a native Win32 message loop, plus a custom `CoroutineDispatcher` whose `dispatch` posts via `PostThreadMessageW(WM_USER_INVOKE, ...)` to that thread. One per JVM, lazily started on first reference.
- `CoInitializeEx COINIT_APARTMENTTHREADED` runs on `WebView2-Thread`, called once at thread start.
- All JNI entry points (`createNative`, `evaluateJavaScriptNative`, `setBoundsNative`, …) execute only on `WebView2-Thread`. The single-thread invariant is unchanged in shape; only the owner thread changes.
- WebView2 callbacks (`onMessage`, `resolveAsset`, `onAcceleratorKeyPressed`, …) arrive on `WebView2-Thread` (since the apartment is STA). Where Kotlin needs to touch Swing state, do an explicit `withContext(Dispatchers.Swing)` inside the callback.
- The EDT pump timer in `WinWebViewEngine.startMessagePump` (`WinWebViewEngine.kt:412`) is **deleted** — `GetMessage` on `WebView2-Thread` is the only message loop now.

### Pump strategy: blocking GetMessage loop (chosen)

`WebView2-Thread` runs a real Win32 message loop in native code: `while GetMessageW(...) { TranslateMessage; DispatchMessage; }`. It sleeps when the queue is empty and wakes the moment Windows posts any message — WebView2 callbacks, focus, paint, input.

Kotlin tasks are marshalled in via `PostThreadMessageW(webview2_tid, WM_USER_INVOKE, 0, raw_ptr_to_box)`; the native loop unboxes the closure and runs it. From any Java thread, `WebView2Dispatcher` becomes a custom `CoroutineDispatcher` whose `dispatch` calls a JNI `postTaskNative` that boxes the `Runnable` and posts it.

Shutdown: `destroyNative` posts `WM_QUIT` to the thread (`PostThreadMessageW(WM_QUIT, ...)`), `GetMessageW` returns `false`, loop exits, thread terminates.

**Why blocking, not tick-based:**

- **Independent of monitor refresh rate.** A 16 ms tick is tied to 60 Hz frame budget. On 120 Hz / 240 Hz / 500 Hz displays, every input or IPC dispatched through the queue inherits a fixed 8–16 ms tail latency that has nothing to do with the actual frame budget. Blocking dispatch gives microsecond latency at any refresh rate.
- **Affects user-perceptible latency:** input (typing, mouse hover effects in WebView2), `webMessage` IPC round-trips between JS and Kotlin, accelerator-key shortcut interop. Page rendering itself is unaffected — modern WebView2 (Chromium) composites via DirectComposition on its own GPU thread, not via `WM_PAINT` — but any signal flowing through our queue is latency-bound.
- **Removes magic numbers:** `PUMP_INTERVAL_MILLIS = 16` and the inner `Duration::from_millis(2)` budget both go away.
- **Idle CPU drops to 0.** No periodic wake when the WebView2 is quiet.
- **Canonical pattern.** WPF, WinForms, Chrome browser process, every well-behaved Win32 UI app does it this way.

**Cost vs the simpler tick approach:** roughly 30 lines of native (WM_USER dispatcher + boxed-closure marshalling + WM_QUIT shutdown), 15 lines of Kotlin (custom `CoroutineDispatcher`), a slightly more involved thread lifecycle. We accept the cost.

#### Rejected alternative: tick-based pump

An earlier draft chose a tick-based pump as "minimally invasive": keep `pumpMessagesNative`, drive it from a coroutine on `WebView2-Thread` every 16 ms, drop the `should_leave_message_for_awt` whitelist (AWT and WebView2 now own different queues so the whitelist would be moot anyway). Rejected because the latency floor scales the wrong way with monitor refresh rate, the magic numbers stay, and the implementation savings are small once the dispatcher is in place.

## Cross-thread HWND caveats

| Concern | Resolution |
|---|---|
| `WM_SETFOCUS` handler in `window_proc` (`src/lib.rs:945-957`) | Unchanged. `WM_SETFOCUS` is delivered to the thread that owns the window — now `WebView2-Thread` — so the handler runs in the right place. |
| `SetFocus(parent_hwnd)` in `clearFocusNative` (`src/lib.rs:431`) | Cross-thread `SetFocus` without `AttachThreadInput` silently fails. **Mitigation:** call `AttachThreadInput(webview2_tid, edt_tid, TRUE)` once after the first `attachToParentNative` (when a valid `parent_hwnd` is known). |
| `GetKeyState` in `current_modifier_flags` (`src/lib.rs:1170`) | Per-thread input state. After `AttachThreadInput`, the queue is shared with EDT — values become correct. |
| `SetParent` in `attachToParentNative` (`src/lib.rs:332`) | Cross-thread `SetParent` works; the existing pattern (hide → SetParent → restore) is preserved. |
| AWT resize parent → child reflow | Already explicit: AWT sends `WM_SIZE` to the parent on EDT, Kotlin then calls `setBounds`. No change. |
| AWT close window → tear-down ordering | `WinWebViewEngine.close` (`WinWebViewEngine.kt:307`) currently uses `runOnEdt` to invoke `destroyNative`; switch to `withContext(WebView2Dispatcher.coroutineDispatcher)`. Before destroying, call `AttachThreadInput(..., FALSE)` to detach. |

## Step-by-step plan

### Step 1. Create the dispatcher and start `WebView2-Thread`

New file `community/platform/ui.webview/src/com/intellij/ui/webview/impl/windows/WebView2Dispatcher.kt`:

```kotlin
internal object WebView2Dispatcher {
    @Volatile private var threadTid: Long = 0

    private val thread: Thread = Thread {
        threadTid = WinWebView2Bridge.currentThreadIdNative()
        WinWebView2Bridge.runMessageLoopNative()  // blocks until WM_QUIT
    }.apply {
        name = "WebView2-Thread"
        isDaemon = true
        uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
            WebViewLogger.LOG.error("Uncaught exception in WebView2-Thread", e)
        }
    }

    val coroutineDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val tid = threadTid
            check(tid != 0L) { "WebView2-Thread is not started yet" }
            WinWebView2Bridge.postTaskNative(block, tid)
        }
    }

    init { thread.start() }
}
```

The thread starts once on first reference and stays alive for the JVM lifetime. Bridge instances come and go; the dispatcher and its thread are shared. No explicit shutdown — the thread is daemon and dies on JVM exit. (If we later need explicit teardown, expose `WinWebView2Bridge.stopMessageLoopNative(threadTid)` to post `WM_QUIT`.)

### Step 2. Replace `runOnEdt` with `WebView2Dispatcher` in `WinWebViewEngine`

`WinWebViewEngine.kt:437-444`:

```kotlin
private fun runOnEdt(action: () -> Unit) {
    if (EDT.isCurrentThreadEdt()) action() else SwingUtilities.invokeLater(action)
}
```

becomes:

```kotlin
private fun invokeOnWebView(action: () -> Unit) {
    WebView2Dispatcher.coroutineDispatcher.dispatch(EmptyCoroutineContext, Runnable { action() })
}
```

Replace every `runOnEdt { ... }` call site in the file (16 occurrences). The mutable engine state (`state.set`, `nativeHandle = ...`) was already thread-safe (`AtomicReference` / `@Volatile`), so cross-thread visibility is preserved.

**Callbacks:**

- `onCreated` (`WinWebViewEngine.kt:83`) is currently wrapped in `runOnEdt`; remove the wrapper — the callback already runs on `WebView2-Thread`.
- `onCreateFailed`, `onMessage`, `onEvaluationResult`, `onEvaluationError`, `onAcceleratorKeyPressed`, `onLog`, `resolveAsset` already run synchronously and stay on `WebView2-Thread`. `inboundMessageHandler(raw)` (`WinWebViewEngine.kt:109`) is no longer on EDT — see Step 6.

### Step 3. Delete the EDT pump timer

`WinWebViewEngine.startMessagePump` / `stopMessagePump` (`WinWebViewEngine.kt:412-433`) used a `javax.swing.Timer` to call `pumpMessagesNative` on EDT every 16 ms. With the blocking message loop, no pump is needed — `WebView2-Thread` already runs `GetMessage` and dispatches everything. Delete `messagePumpTimer`, both methods, the constants `PUMP_INTERVAL_MILLIS` and `MAX_PUMP_MESSAGES_PER_TICK`, and every `startMessagePump()` / `stopMessagePump()` call site.

### Step 4. `AttachThreadInput`

New JNI methods:

```rust
#[no_mangle]
pub extern "system" fn Java_..._attachThreadInputNative(
    _env: JNIEnv<'_>, _class: JClass<'_>, edt_tid: jlong,
) {
    let webview_tid = unsafe { GetCurrentThreadId() };
    unsafe { let _ = AttachThreadInput(webview_tid, edt_tid as u32, true); }
}

#[no_mangle]
pub extern "system" fn Java_..._detachThreadInputNative(
    _env: JNIEnv<'_>, _class: JClass<'_>, edt_tid: jlong,
) {
    let webview_tid = unsafe { GetCurrentThreadId() };
    unsafe { let _ = AttachThreadInput(webview_tid, edt_tid as u32, false); }
}
```

Kotlin: on first `attachToParent`, capture EDT's Win32 thread id and call `attachThreadInputNative` from `WebView2-Thread`. The Win32 TID for EDT can be obtained by adding a small native helper `getCurrentThreadIdNative()` and calling it once on EDT (e.g. via `SwingUtilities.invokeAndWait`). The Java thread id is not the same as the Win32 TID; do not confuse them.

### Step 5. Replace `pump_pending_messages_limited` with `runMessageLoopNative` in Rust

New native entry points:

```rust
const WM_USER_INVOKE: u32 = WM_USER + 1;

#[no_mangle]
pub extern "system" fn Java_..._runMessageLoopNative(
    _env: JNIEnv<'_>, _class: JClass<'_>,
) {
    let mut msg = MSG::default();
    while unsafe { GetMessageW(&mut msg, None, 0, 0).as_bool() } {
        if msg.message == WM_USER_INVOKE {
            // Box<Box<dyn FnOnce() + Send>> arrives via lParam
            let task: Box<Box<dyn FnOnce() + Send>> =
                unsafe { Box::from_raw(msg.lParam.0 as *mut _) };
            task();
            continue;
        }
        unsafe {
            let _ = TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }
    // GetMessage returned false → WM_QUIT received → loop exits
}

#[no_mangle]
pub extern "system" fn Java_..._currentThreadIdNative(
    _env: JNIEnv<'_>, _class: JClass<'_>,
) -> jlong {
    unsafe { GetCurrentThreadId() as jlong }
}

#[no_mangle]
pub extern "system" fn Java_..._postTaskNative(
    env: JNIEnv<'_>, _class: JClass<'_>, runnable: JObject<'_>, target_tid: jlong,
) {
    let vm = env.get_java_vm().expect("JavaVM");
    let runnable_global = env.new_global_ref(runnable).expect("global ref");
    let task: Box<dyn FnOnce() + Send> = Box::new(move || {
        let mut env = vm.attach_current_thread().expect("attach");
        let _ = env.call_method(runnable_global.as_obj(), "run", "()V", &[]);
    });
    let raw = Box::into_raw(Box::new(task)) as isize;
    unsafe {
        let _ = PostThreadMessageW(target_tid as u32, WM_USER_INVOKE, WPARAM(0), LPARAM(raw));
    }
}

#[no_mangle]
pub extern "system" fn Java_..._stopMessageLoopNative(
    _env: JNIEnv<'_>, _class: JClass<'_>, target_tid: jlong,
) {
    unsafe {
        let _ = PostThreadMessageW(target_tid as u32, WM_QUIT, WPARAM(0), LPARAM(0));
    }
}
```

The old `pump_pending_messages_limited`, `should_leave_message_for_awt`, `is_message_in_range`, `PumpResult`, and `Java_..._pumpMessagesNative` (`src/lib.rs:1082-1149`) are all deleted — about 70 lines removed.

On `WebView2-Thread` startup the thread caches its Win32 TID once via `currentThreadIdNative` (so other threads can target it with `PostThreadMessageW`), then enters `runMessageLoopNative`. From any Java thread, `WebView2Dispatcher.dispatch` posts a `Runnable` via `postTaskNative`; the native loop wakes, unboxes the closure, and runs it on the right thread.

### Step 6. Audit callback consumers

The Kotlin callbacks currently assume EDT (e.g. `inboundMessageHandler` at `WinWebViewEngine.kt:109` invokes `WebViewMessageBusImpl.transferFromJs`). Trouble spots:

- `WebViewMessageBusImpl.transferFromJs` — verify whether it requires EDT. If not, leave as is. If yes, wrap with `withContext(Dispatchers.Swing)` before the call.
- `WinWebViewShortcutInterop.handleAcceleratorKeyPressed` (`WinWebViewEngine.kt:122`) synchronously touches a Swing `Component` (`shortcutTarget`). It almost certainly needs EDT. Options: (a) `SwingUtilities.invokeAndWait` with a timeout — deadlock-prone; (b) refactor to async, returning `handled = false` synchronously and posting the work, letting WebView2 handle the key by default. Owners of this interop should weigh in.
- `resolveAsset` is now invoked from `WebView2-Thread`. Verify that `WebViewAssetResolver.resolveWebViewAssetUrl` does not touch EDT-only state. (`PathManager` and the file system are fine.)

### Step 7. Add an ownership invariant in Rust

At the top of `lib.rs`:

```rust
thread_local! { static OWNING_THREAD: Cell<Option<u32>> = Cell::new(None); }

fn assert_owning_thread() {
    debug_assert!(
        OWNING_THREAD.with(|t| t.get()) == Some(unsafe { GetCurrentThreadId() }),
        "WinWebView2Bridge JNI methods must be called from WebView2-Thread"
    );
}
```

Set in `create_native`, check in `run_with_handle` and `destroyNative`.

### Step 8. Run the smoke tests

`WindowsWebViewSmokeTest`:

- `evaluateJavaScript_returnsResult` — should pass unchanged.
- `loadHtml_beforeAttach_isAppliedAfterAttach` — pending state is now applied on `WebView2-Thread`; should still work.
- `webMessageReceived_reachesBus` — the callback arrives on `WebView2-Thread`. Confirm that `WebViewMessageBusImpl` accepts that.
- **`swingTextField_acceptsTypingAfterWebViewFocus`** — primary canary. Clicks the host, then the text field, types digits. Validates focus traversal between AWT and the WebView2 child. Without `AttachThreadInput`, this test fails.
- `facade_survives_host_detach_reattach` — exercises cross-thread `SetParent` for regressions.

## Files to touch

| File | Change |
|---|---|
| `community/platform/ui.webview/native/WinWebView2Bridge/src/lib.rs` | + `runMessageLoopNative` / `postTaskNative` / `stopMessageLoopNative` / `currentThreadIdNative` / `attachThreadInputNative` / `detachThreadInputNative`; − `pump_pending_messages_limited`, `should_leave_message_for_awt`, `is_message_in_range`, `PumpResult`, `Java_..._pumpMessagesNative` (~70 lines); add `assert_owning_thread` in `run_with_handle`. |
| `community/platform/ui.webview/src/com/intellij/ui/webview/impl/windows/WebView2Dispatcher.kt` | **New file:** daemon `WebView2-Thread` runs `runMessageLoopNative`; custom `CoroutineDispatcher.dispatch` posts via `postTaskNative`. |
| `community/platform/ui.webview/src/com/intellij/ui/webview/impl/windows/WinWebView2Bridge.kt` | + `external fun runMessageLoopNative()`, `postTaskNative(runnable: Runnable, targetTid: Long)`, `stopMessageLoopNative(targetTid: Long)`, `currentThreadIdNative(): Long`, `attachThreadInputNative(edtTid: Long)`, `detachThreadInputNative(edtTid: Long)`; − `pumpMessagesNative`; bump ABI sentinel `wvi-scoped-pump-v1` → `wvi-dedicated-thread-v1`. |
| `community/platform/ui.webview/src/com/intellij/ui/webview/impl/windows/WinWebViewEngine.kt` | `runOnEdt` → `invokeOnWebView`; **delete** `startMessagePump`, `stopMessagePump`, `messagePumpTimer`, `PUMP_INTERVAL_MILLIS`, `MAX_PUMP_MESSAGES_PER_TICK` and all call sites; `AttachThreadInput` on first attach; `DetachThreadInput` on close. Possible `withContext(Dispatchers.Swing)` wrappers in callbacks per Step 6. |
| `community/platform/ui.webview/src/com/intellij/ui/webview/impl/windows/WinWebViewShortcutInterop.kt` (if present) | Possible refactor — see Step 6. |

## Stages (each shippable independently)

1. **Stage 1 (dispatcher + message loop, native + Kotlin).** Steps 1–3 + 5 — `WebView2Dispatcher`, native `runMessageLoopNative` / `postTaskNative` / `stopMessageLoopNative` / `currentThreadIdNative`, delete tick pump and AWT whitelist on both sides, switch all `runOnEdt` call sites. After this stage the latency floor is gone, idle CPU drops to 0, and the 16 ms / 2 ms / 16-message magic numbers are removed. Cross-thread focus may glitch — Stage 2 fixes it.
2. **Stage 2 (focus interop).** Steps 4 + 6 — `AttachThreadInput`, fix the focus/keyboard interop and `WinWebViewShortcutInterop`. After this the smoke tests should be green.
3. **Stage 3 (Rust hardening).** Step 7 — `assert_owning_thread` invariant. Pure hygiene; not required for correctness.

Each stage is its own commit/MR. Run `WindowsWebViewSmokeTest` between stages.

## Estimate

| Stage | Time |
|---|---|
| 1 (dispatcher + native message loop) | ~2 days |
| 2 (`AttachThreadInput` + focus interop) | ~2 days + 1–2 days of stabilization |
| 3 (Rust hardening) | ~0.5 day |
| **Total** | **~5 dev days + 1–2 days of Windows test stabilization** |

The risk is concentrated in Stage 2 — cross-thread input/focus in Win32 tends to surface flaky bugs that only show up on a real Windows host.

## Risks

| Risk | Mitigation |
|---|---|
| `WinWebViewShortcutInterop.handleAcceleratorKeyPressed` requires EDT and would block the accelerator callback | Refactor to async return (handled = false, do work later), or `SwingUtilities.invokeAndWait` with a timeout. |
| WebView2 callback arrives on the wrong thread for some unexpected reason | Add `assert_owning_thread()` inside the `attach_*_handler` closures in Rust. |
| `inboundMessageHandler` consumer expects EDT | Wrap `inboundMessageHandler(raw)` in `withContext(Dispatchers.Swing)` if needed. |
| WebView2 environment user-data-dir conflict on rapid recreate | Unrelated to this refactor; tracked separately. |

## Verification

- Before and after each stage, run `WindowsWebViewSmokeTest` on a Windows host (non-headless).
- After Stage 2, exercise the IDE manually — open a feature using `WinWebView`, test typing/focus/IME (Russian input, Compose, Alt-Tab).
- Measure EDT latency on a load-heavy page (busy WebMessage flood) before and after. Expectation: hitches under load before, smooth after.
- `tasklist /v` / Process Hacker: confirm a `WebView2-Thread` daemon shows up and does not multiply per bridge instance.
