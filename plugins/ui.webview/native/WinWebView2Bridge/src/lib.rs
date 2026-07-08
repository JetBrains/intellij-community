// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#![cfg(target_os = "windows")]

use std::{
    cell::{Cell, RefCell},
    collections::{HashMap, VecDeque},
    ffi::c_void,
    panic::AssertUnwindSafe,
    rc::Rc,
    time::Instant,
};

use jni::{
    objects::{GlobalRef, JByteArray, JClass, JObject, JString, JValue},
    sys::{jboolean, jdouble, jint, jlong, jstring},
    JNIEnv, JavaVM,
};
use webview2_com::{Microsoft::Web::WebView2::Win32::*, *};
use windows::{
    core::{w, Interface, HSTRING, PCWSTR, PWSTR},
    Win32::{
        Foundation::*,
        Graphics::Gdi::*,
        System::{Com::*, LibraryLoader::GetModuleHandleW, Threading::GetCurrentThreadId},
        UI::{
            Input::KeyboardAndMouse::{
                GetKeyState, SetFocus, VIRTUAL_KEY, VK_CONTROL, VK_LSHIFT, VK_LWIN, VK_MENU,
                VK_RSHIFT, VK_RWIN, VK_SHIFT,
            },
            Shell::SHCreateMemStream,
            WindowsAndMessaging::*,
        },
    },
};

type BridgeResult<T> = std::result::Result<T, String>;
type NativeHandle = Rc<RefCell<NativeWebView>>;
type EventRegistrationToken = i64;

const MODIFIER_SHIFT: jint = 1;
const MODIFIER_CONTROL: jint = 1 << 1;
const MODIFIER_ALT: jint = 1 << 2;
const MODIFIER_META: jint = 1 << 3;
const NATIVE_ABI_VERSION: &str = "wvi-custom-scheme-assets-v10";
const WM_USER_INVOKE: u32 = WM_USER + 1;
const WM_USER_SHIFT_FALLBACK: u32 = WM_USER + 2;
const WEBVIEW_ASSET_CUSTOM_SCHEME: &str = "ij-webview-asset";
const WEBVIEW_ASSET_CUSTOM_SCHEME_FILTER: &str = "ij-webview-asset://assets/*";
const WEBVIEW_ASSET_HTTPS_FILTER: &str = "https://ij-webview-assets.local/*";
const DIAGNOSTIC_TRACE: jint = 0;
const DIAGNOSTIC_DEBUG: jint = 1;
const DIAGNOSTIC_INFO: jint = 2;
const DIAGNOSTIC_WARN: jint = 3;
const DIAGNOSTIC_ERROR: jint = 4;

thread_local! {
    static KEYBOARD_INTEROP_WINDOWS: RefCell<Vec<HWND>> = RefCell::new(Vec::new());
    static KEYBOARD_INTEROP_HOOK: RefCell<Option<HHOOK>> = const { RefCell::new(None) };
    static SHIFT_FALLBACK_EVENTS: RefCell<VecDeque<PendingShiftEvent>> = RefCell::new(VecDeque::new());
    static NEXT_SHIFT_FALLBACK_EVENT_ID: Cell<usize> = const { Cell::new(1) };
}

struct PendingShiftEvent {
    id: usize,
    hwnd: HWND,
    key_event_kind: jint,
    virtual_key: jint,
    modifiers: jint,
    key_event_lparam: jint,
}

struct NativeAssetResponse {
    status_code: i32,
    status_text: String,
    headers: String,
    bytes: Vec<u8>,
}

#[derive(Clone, PartialEq, Eq)]
struct EnvironmentKey {
    user_data_dir: String,
}

impl EnvironmentKey {
    fn new(user_data_dir: String) -> Self {
        Self { user_data_dir }
    }
}

struct JavaCallbacks {
    vm: JavaVM,
    object: GlobalRef,
}

impl JavaCallbacks {
    fn on_created(&self, handle: jlong) {
        self.with_env(|env, object| {
            env.call_method(object, "onCreated", "(J)V", &[JValue::Long(handle)])?;
            Ok(())
        });
    }

    fn on_create_failed(&self, message: String) {
        self.with_env(|env, object| {
            let message = JObject::from(env.new_string(message)?);
            env.call_method(
                object,
                "onCreateFailed",
                "(Ljava/lang/String;)V",
                &[JValue::Object(&message)],
            )?;
            Ok(())
        });
    }

    fn on_message(&self, raw: String) {
        self.with_env(|env, object| {
            let raw = JObject::from(env.new_string(raw)?);
            env.call_method(
                object,
                "onMessage",
                "(Ljava/lang/String;)V",
                &[JValue::Object(&raw)],
            )?;
            Ok(())
        });
    }

    fn on_evaluation_result(&self, eval_id: jlong, result: String) {
        self.with_env(|env, object| {
            let result = JObject::from(env.new_string(result)?);
            env.call_method(
                object,
                "onEvaluationResult",
                "(JLjava/lang/String;)V",
                &[JValue::Long(eval_id), JValue::Object(&result)],
            )?;
            Ok(())
        });
    }

    fn on_evaluation_error(&self, eval_id: jlong, message: String) {
        self.with_env(|env, object| {
            let message = JObject::from(env.new_string(message)?);
            env.call_method(
                object,
                "onEvaluationError",
                "(JLjava/lang/String;)V",
                &[JValue::Long(eval_id), JValue::Object(&message)],
            )?;
            Ok(())
        });
    }

    fn on_dev_tools_protocol_method_result(
        &self,
        call_id: jlong,
        result: Option<String>,
        error: Option<String>,
    ) {
        self.with_env(|env, object| {
            let result = match result {
                Some(result) => JObject::from(env.new_string(result)?),
                None => JObject::null(),
            };
            let error = match error {
                Some(error) => JObject::from(env.new_string(error)?),
                None => JObject::null(),
            };
            env.call_method(
                object,
                "onDevToolsProtocolMethodResult",
                "(JLjava/lang/String;Ljava/lang/String;)V",
                &[
                    JValue::Long(call_id),
                    JValue::Object(&result),
                    JValue::Object(&error),
                ],
            )?;
            Ok(())
        });
    }

    fn on_accelerator_key_pressed(
        &self,
        key_event_kind: jint,
        virtual_key: jint,
        modifiers: jint,
        key_event_lparam: jint,
    ) -> bool {
        let Ok(mut env) = self.vm.attach_current_thread() else {
            return false;
        };
        env.call_method(
            self.object.as_obj(),
            "onAcceleratorKeyPressed",
            "(IIII)Z",
            &[
                JValue::Int(key_event_kind),
                JValue::Int(virtual_key),
                JValue::Int(modifiers),
                JValue::Int(key_event_lparam),
            ],
        )
        .ok()
        .and_then(|value| value.z().ok())
        .unwrap_or(false)
    }

    fn on_focus_gained(&self) {
        self.with_env(|env, object| {
            env.call_method(object, "onFocusGained", "()V", &[])?;
            Ok(())
        });
    }

    fn on_before_mouse_focus(&self) {
        self.with_env(|env, object| {
            env.call_method(object, "onBeforeMouseFocus", "()V", &[])?;
            Ok(())
        });
    }

    #[allow(dead_code)]
    fn on_log(&self, level: jint, message: String) {
        self.with_env(|env, object| {
            let message = JObject::from(env.new_string(message)?);
            env.call_method(
                object,
                "onLog",
                "(ILjava/lang/String;)V",
                &[JValue::Int(level), JValue::Object(&message)],
            )?;
            Ok(())
        });
    }

    fn on_native_diagnostic(&self, level: jint, event: &str, message: String, data: String) {
        self.with_env(|env, object| {
            let event = JObject::from(env.new_string(event)?);
            let message = JObject::from(env.new_string(message)?);
            let data = JObject::from(env.new_string(data)?);
            env.call_method(
                object,
                "onNativeDiagnostic",
                "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                &[
                    JValue::Int(level),
                    JValue::Object(&event),
                    JValue::Object(&message),
                    JValue::Object(&data),
                ],
            )?;
            Ok(())
        });
    }

    fn resolve_asset(&self, url: String) -> BridgeResult<Option<NativeAssetResponse>> {
        let mut env = self.vm.attach_current_thread().map_err(format_jni_error)?;
        let url = JObject::from(env.new_string(url).map_err(format_jni_error)?);
        let response = env
            .call_method(
                self.object.as_obj(),
                "resolveAsset",
                "(Ljava/lang/String;)Lcom/intellij/ui/webview/impl/windows/WinWebView2Bridge$AssetResponse;",
                &[JValue::Object(&url)],
            )
            .map_err(format_jni_error)?
            .l()
            .map_err(format_jni_error)?;
        if response.is_null() {
            return Ok(None);
        }

        let status_code = env
            .call_method(&response, "getStatusCode", "()I", &[])
            .map_err(format_jni_error)?
            .i()
            .map_err(format_jni_error)?;
        let status_text = call_string_getter(&mut env, &response, "getStatusText")?;
        let headers = call_string_getter(&mut env, &response, "getHeaders")?;
        let bytes = env
            .call_method(&response, "getBytes", "()[B", &[])
            .map_err(format_jni_error)?
            .l()
            .map_err(format_jni_error)?;
        let bytes = env
            .convert_byte_array(JByteArray::from(bytes))
            .map_err(format_jni_error)?;
        Ok(Some(NativeAssetResponse {
            status_code,
            status_text,
            headers,
            bytes,
        }))
    }

    fn with_env<F>(&self, action: F)
    where
        F: FnOnce(&mut JNIEnv<'_>, &JObject<'_>) -> jni::errors::Result<()>,
    {
        let Ok(mut env) = self.vm.attach_current_thread() else {
            return;
        };
        let _ = action(&mut env, self.object.as_obj());
    }
}

struct SharedEnvironmentState {
    key: EnvironmentKey,
    generation: u64,
    started_at: Instant,
    environment: Option<ICoreWebView2Environment>,
    creating: bool,
    waiters: Vec<NativeHandle>,
    active_views: Vec<NativeHandle>,
    environment_completed_handler:
        Option<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>,
}

#[derive(Default)]
struct SharedWebView2EnvironmentManager {
    state: Option<SharedEnvironmentState>,
    next_generation: u64,
}

enum SharedEnvironmentAction {
    None,
    StartEnvironment {
        user_data_dir: String,
        generation: u64,
        handler: ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler,
    },
    CreateController {
        environment: ICoreWebView2Environment,
        generation: u64,
    },
}

impl SharedWebView2EnvironmentManager {
    fn ensure_environment(
        &mut self,
        key: EnvironmentKey,
        native: NativeHandle,
    ) -> BridgeResult<SharedEnvironmentAction> {
        if let Some(state) = self.state.as_mut() {
            if state.key != key {
                return Err(format!(
                    "WebView2 shared environment already exists for another user data directory: requested={}, active={}",
                    key.user_data_dir, state.key.user_data_dir
                ));
            }
            if let Some(environment) = &state.environment {
                return Ok(SharedEnvironmentAction::CreateController {
                    environment: environment.clone(),
                    generation: state.generation,
                });
            }
            if state.creating {
                state.waiters.push(native);
                return Ok(SharedEnvironmentAction::None);
            }
        }

        let generation = self.next_generation;
        self.next_generation += 1;
        let handler = create_shared_environment_completed_handler(generation);
        self.state = Some(SharedEnvironmentState {
            key: key.clone(),
            generation,
            started_at: Instant::now(),
            environment: None,
            creating: true,
            waiters: vec![native],
            active_views: Vec::new(),
            environment_completed_handler: Some(handler.clone()),
        });
        Ok(SharedEnvironmentAction::StartEnvironment {
            user_data_dir: key.user_data_dir,
            generation,
            handler,
        })
    }

    fn clear_start_failure(&mut self, generation: u64) {
        if self
            .state
            .as_ref()
            .is_some_and(|state| state.generation == generation && state.creating)
        {
            self.state = None;
        }
    }

    fn complete_environment_failure(&mut self, generation: u64) -> Vec<NativeHandle> {
        let Some(state) = self.state.as_ref() else {
            return Vec::new();
        };
        if state.generation != generation {
            return Vec::new();
        }
        let state = self.state.take().unwrap();
        state.waiters
    }

    fn complete_environment_success(
        &mut self,
        generation: u64,
        environment: ICoreWebView2Environment,
    ) -> Vec<NativeHandle> {
        let Some(state) = self.state.as_mut() else {
            return Vec::new();
        };
        if state.generation != generation {
            return Vec::new();
        }
        state.environment = Some(environment);
        state.creating = false;
        state.environment_completed_handler = None;
        std::mem::take(&mut state.waiters)
    }

    fn register_active_view(&mut self, generation: u64, native: NativeHandle) -> bool {
        let Some(state) = self.state.as_mut() else {
            return false;
        };
        if state.generation != generation || is_native_destroyed(&native) {
            return false;
        }
        prune_destroyed_views(&mut state.active_views);
        let handle = native_handle(&native);
        if handle != 0
            && state
                .active_views
                .iter()
                .all(|view| native_handle(view) != handle)
        {
            state.active_views.push(native);
        }
        true
    }

    fn unregister_view(&mut self, handle: jlong) {
        let Some(state) = self.state.as_mut() else {
            return;
        };
        remove_view_handle(&mut state.waiters, handle);
        remove_view_handle(&mut state.active_views, handle);
    }

    fn active_views(&mut self, generation: u64) -> Vec<NativeHandle> {
        let Some(state) = self.state.as_mut() else {
            return Vec::new();
        };
        if state.generation != generation {
            return Vec::new();
        }
        prune_destroyed_views(&mut state.active_views);
        state.active_views.clone()
    }

    fn invalidate_environment(&mut self, generation: u64) -> Vec<NativeHandle> {
        let Some(state) = self.state.as_ref() else {
            return Vec::new();
        };
        if state.generation != generation {
            return Vec::new();
        }
        let mut state = self.state.take().unwrap();
        prune_destroyed_views(&mut state.waiters);
        prune_destroyed_views(&mut state.active_views);
        state.waiters.extend(state.active_views);
        state.waiters
    }

    fn is_current_generation(&self, generation: u64) -> bool {
        self.state
            .as_ref()
            .is_some_and(|state| state.generation == generation)
    }

    fn environment_started_at(&self, generation: u64) -> Option<Instant> {
        self.state.as_ref().and_then(|state| {
            if state.generation == generation {
                Some(state.started_at)
            } else {
                None
            }
        })
    }
}

thread_local! {
    static SHARED_ENVIRONMENT_MANAGER: RefCell<SharedWebView2EnvironmentManager> =
        RefCell::new(SharedWebView2EnvironmentManager::default());
}

#[derive(Clone, Copy)]
struct NavigationTiming {
    requested_at: Option<Instant>,
    started_at: Instant,
}

struct NativeWebView {
    handle: jlong,
    parent: HWND,
    hwnd: HWND,
    controller: Option<ICoreWebView2Controller>,
    webview: Option<ICoreWebView2>,
    controller_completed_handler: Option<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler>,
    controller_create_started_at: Option<Instant>,
    last_navigation_requested_at: Option<Instant>,
    navigation_timings: HashMap<u64, NavigationTiming>,
    unidentified_navigation_timing: Option<NavigationTiming>,
    add_script_handlers: Vec<(
        u64,
        ICoreWebView2AddScriptToExecuteOnDocumentCreatedCompletedHandler,
    )>,
    execute_script_handlers: Vec<(u64, ICoreWebView2ExecuteScriptCompletedHandler)>,
    dev_tools_handlers: Vec<(u64, ICoreWebView2CallDevToolsProtocolMethodCompletedHandler)>,
    next_script_handler_id: u64,
    document_start_scripts: Vec<String>,
    web_message_token: EventRegistrationToken,
    web_resource_requested_token: Option<EventRegistrationToken>,
    accelerator_key_pressed_token: Option<EventRegistrationToken>,
    got_focus_token: Option<EventRegistrationToken>,
    callbacks: Rc<JavaCallbacks>,
    destroyed: bool,
    visible: bool,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    scale: f64,
}

impl NativeWebView {
    fn destroy(&mut self) {
        if self.destroyed {
            return;
        }
        self.destroyed = true;
        if let (Some(controller), Some(token)) =
            (&self.controller, self.accelerator_key_pressed_token.take())
        {
            unsafe {
                let _ = controller.remove_AcceleratorKeyPressed(token);
            }
        }
        if let (Some(controller), Some(token)) = (&self.controller, self.got_focus_token.take()) {
            unsafe {
                let _ = controller.remove_GotFocus(token);
            }
        }
        if let (Some(webview), Some(token)) =
            (&self.webview, self.web_resource_requested_token.take())
        {
            unsafe {
                let _ = webview.remove_WebResourceRequested(token);
            }
            remove_web_resource_requested_filter(webview, WEBVIEW_ASSET_CUSTOM_SCHEME_FILTER);
            remove_web_resource_requested_filter(webview, WEBVIEW_ASSET_HTTPS_FILTER);
        }
        self.webview = None;
        self.controller = None;
        self.controller_completed_handler = None;
        self.add_script_handlers.clear();
        self.execute_script_handlers.clear();
        self.dev_tools_handlers.clear();
        if !self.hwnd.0.is_null() {
            unsafe {
                let _ = DestroyWindow(self.hwnd);
            }
            self.hwnd = HWND::default();
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_abiVersionNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    env.new_string(NATIVE_ABI_VERSION)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_createNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    parent_hwnd: jlong,
    user_data_dir: JString<'_>,
    document_start_script: JString<'_>,
    callbacks: JObject<'_>,
) -> jlong {
    match create_native(
        &mut env,
        parent_hwnd,
        user_data_dir,
        document_start_script,
        callbacks,
    ) {
        Ok(handle) => handle,
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_destroyNative(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    unregister_shared_environment_view(handle);
    unsafe {
        let native = Rc::from_raw(handle as *const RefCell<NativeWebView>);
        native.borrow_mut().destroy();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_attachToParentNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    parent_hwnd: jlong,
) {
    run_with_handle(&mut env, handle, |native| {
        let parent = HWND(parent_hwnd as *mut c_void);
        let mut view = native.borrow_mut();
        view.parent = parent;
        unsafe {
            if let Some(controller) = &view.controller {
                controller
                    .SetIsVisible(false)
                    .map_err(format_windows_error)?;
            }
            let _ = ShowWindow(view.hwnd, SW_HIDE);
            SetParent(view.hwnd, Some(parent)).map_err(format_windows_error)?;
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_detachFromParentNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, |native| {
        let mut view = native.borrow_mut();
        unsafe {
            if let Some(controller) = &view.controller {
                let _ = controller.SetIsVisible(false);
            }
            let _ = ShowWindow(view.hwnd, SW_HIDE);
            let _ = SetParent(view.hwnd, None);
        }
        view.parent = HWND::default();
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_setBoundsNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jint,
    y: jint,
    width: jint,
    height: jint,
    scale: jdouble,
) {
    run_with_handle(&mut env, handle, |native| {
        let mut view = native.borrow_mut();
        view.x = x;
        view.y = y;
        view.width = width.max(0);
        view.height = height.max(0);
        view.scale = if scale > 0.0 { scale } else { 1.0 };
        apply_bounds(&view)
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_setVisibleNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    visible: jboolean,
) {
    run_with_handle(&mut env, handle, |native| {
        let mut view = native.borrow_mut();
        view.visible = visible != 0;
        unsafe {
            let _ = ShowWindow(view.hwnd, if view.visible { SW_SHOW } else { SW_HIDE });
            if let Some(controller) = &view.controller {
                controller
                    .SetIsVisible(view.visible)
                    .map_err(format_windows_error)?;
            }
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_focusNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, |native| {
        let view = native.borrow();
        unsafe {
            let _ = SetFocus(Some(view.hwnd));
            if let Some(controller) = &view.controller {
                controller
                    .MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC)
                    .map_err(format_windows_error)?;
            }
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_clearFocusNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, |native| {
        let view = native.borrow();
        unsafe {
            let _ = SetFocus(Some(view.parent));
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_loadUrlNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    url: JString<'_>,
) {
    let Ok(url) = jstring_to_string(&mut env, url) else {
        return;
    };
    run_with_handle(&mut env, handle, |native| {
        let webview = {
            let mut view = native.borrow_mut();
            view.last_navigation_requested_at = Some(Instant::now());
            view.navigation_timings.clear();
            view.unidentified_navigation_timing = None;
            view.webview
                .clone()
                .ok_or_else(|| "WebView2 is not ready".to_string())?
        };
        measure_perf(
            &native,
            "perf.webview2.navigation.loadUrl.call",
            vec![("urlChars", url.len().to_string())],
            || unsafe {
                webview
                    .Navigate(&HSTRING::from(url.as_str()))
                    .map_err(format_windows_error)
            },
        )?;
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_setVirtualHostNameToFolderMappingNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    host_name: JString<'_>,
    folder_path: JString<'_>,
) {
    let host_name = match jstring_to_string(&mut env, host_name) {
        Ok(value) => value,
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", message);
            return;
        }
    };
    let folder_path = match jstring_to_string(&mut env, folder_path) {
        Ok(value) => value,
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", message);
            return;
        }
    };
    run_with_handle(&mut env, handle, |native| {
        let view = native.borrow();
        let webview = view
            .webview
            .as_ref()
            .ok_or_else(|| "WebView2 is not ready".to_string())?;
        set_virtual_host_name_to_folder_mapping(webview, &host_name, &folder_path)
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_loadHtmlNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    html: JString<'_>,
    _base_url: JObject<'_>,
) {
    let Ok(html) = jstring_to_string(&mut env, html) else {
        return;
    };
    run_with_handle(&mut env, handle, |native| {
        let webview = {
            let mut view = native.borrow_mut();
            view.last_navigation_requested_at = Some(Instant::now());
            view.navigation_timings.clear();
            view.unidentified_navigation_timing = None;
            view.webview
                .clone()
                .ok_or_else(|| "WebView2 is not ready".to_string())?
        };
        measure_perf(
            &native,
            "perf.webview2.navigation.loadHtml.call",
            vec![("htmlChars", html.len().to_string())],
            || unsafe {
                webview
                    .NavigateToString(&HSTRING::from(html.as_str()))
                    .map_err(format_windows_error)
            },
        )?;
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_evaluateJavaScriptNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    eval_id: jlong,
    script: JString<'_>,
) {
    let Ok(script) = jstring_to_string(&mut env, script) else {
        return;
    };
    run_with_handle(&mut env, handle, |native| {
        let (webview, handler, handler_id) = {
            let mut view = native.borrow_mut();
            let webview = view
                .webview
                .clone()
                .ok_or_else(|| "WebView2 is not ready".to_string())?;
            let callbacks = view.callbacks.clone();
            let handler_id = view.next_script_handler_id;
            view.next_script_handler_id += 1;
            let native_for_callback = native.clone();
            let handler =
                ExecuteScriptCompletedHandler::create(Box::new(move |error_code, result| {
                    remove_execute_script_handler(&native_for_callback, handler_id);
                    match error_code {
                        Ok(()) => callbacks.on_evaluation_result(eval_id, result),
                        Err(error) => {
                            callbacks.on_evaluation_error(eval_id, format_windows_error(error))
                        }
                    }
                    Ok(())
                }));
            view.execute_script_handlers
                .push((handler_id, handler.clone()));
            (webview, handler, handler_id)
        };
        let result = unsafe { webview.ExecuteScript(&HSTRING::from(script), &handler) };
        if let Err(error) = result {
            remove_execute_script_handler(&native, handler_id);
            return Err(format_windows_error(error));
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_callDevToolsProtocolMethodNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    call_id: jlong,
    method_name: JString<'_>,
    params_json: JString<'_>,
) {
    let method_name = match jstring_to_string(&mut env, method_name) {
        Ok(value) => value,
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", message);
            return;
        }
    };
    let params_json = match jstring_to_string(&mut env, params_json) {
        Ok(value) => value,
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", message);
            return;
        }
    };
    run_with_handle(&mut env, handle, |native| {
        let (webview, handler, handler_id) = {
            let mut view = native.borrow_mut();
            let webview = view
                .webview
                .clone()
                .ok_or_else(|| "WebView2 is not ready".to_string())?;
            let callbacks = view.callbacks.clone();
            let handler_id = view.next_script_handler_id;
            view.next_script_handler_id += 1;
            let native_for_callback = native.clone();
            let handler = CallDevToolsProtocolMethodCompletedHandler::create(Box::new(
                move |error_code, result| {
                    remove_dev_tools_handler(&native_for_callback, handler_id);
                    match error_code {
                        Ok(()) => callbacks.on_dev_tools_protocol_method_result(
                            call_id,
                            Some(result),
                            None,
                        ),
                        Err(error) => callbacks.on_dev_tools_protocol_method_result(
                            call_id,
                            None,
                            Some(format_windows_error(error)),
                        ),
                    }
                    Ok(())
                },
            ));
            view.dev_tools_handlers.push((handler_id, handler.clone()));
            (webview, handler, handler_id)
        };
        let result = unsafe {
            webview.CallDevToolsProtocolMethod(
                &HSTRING::from(method_name.as_str()),
                &HSTRING::from(params_json.as_str()),
                &handler,
            )
        };
        if let Err(error) = result {
            remove_dev_tools_handler(&native, handler_id);
            return Err(format_windows_error(error));
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_transferToJsNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    raw_json: JString<'_>,
) {
    let Ok(raw_json) = jstring_to_string(&mut env, raw_json) else {
        return;
    };
    let script = format!(
        /*language=JavaScript*/ "window.__WVI__ && window.__WVI__.__deliver({});",
        js_string_literal(&raw_json)
    );
    run_with_handle(&mut env, handle, |native| {
        let (webview, handler, handler_id) = {
            let mut view = native.borrow_mut();
            let webview = view
                .webview
                .clone()
                .ok_or_else(|| "WebView2 is not ready".to_string())?;
            let handler_id = view.next_script_handler_id;
            view.next_script_handler_id += 1;
            let native_for_callback = native.clone();
            let handler = ExecuteScriptCompletedHandler::create(Box::new(move |_, _| {
                remove_execute_script_handler(&native_for_callback, handler_id);
                Ok(())
            }));
            view.execute_script_handlers
                .push((handler_id, handler.clone()));
            (webview, handler, handler_id)
        };
        let result = unsafe { webview.ExecuteScript(&HSTRING::from(script), &handler) };
        if let Err(error) = result {
            remove_execute_script_handler(&native, handler_id);
            return Err(format_windows_error(error));
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_currentThreadIdNative(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jlong {
    unsafe {
        // Force creation of the message queue so that PostThreadMessageW from other
        // threads cannot race with the queue's first use inside runMessageLoopNative.
        let mut msg = MSG::default();
        let _ = PeekMessageW(&mut msg, None, WM_USER, WM_USER, PM_NOREMOVE);
        GetCurrentThreadId() as jlong
    }
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_runMessageLoopNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    let mut msg = MSG::default();
    unsafe {
        loop {
            let ret = GetMessageW(&mut msg, None, 0, 0).0;
            if ret == 0 {
                // WM_QUIT received; exit normally.
                break;
            }
            if ret < 0 {
                // System error in GetMessageW; the thread queue is broken.
                report_dispatcher_error(
                    &mut env,
                    "WebView2 dispatcher: GetMessageW returned an error; exiting message loop",
                );
                break;
            }
            // Only thread-targeted messages (PostThreadMessageW) carry our task pointer.
            // Window messages from WebView2/COM with msg.message == WM_USER_INVOKE must
            // pass through to DispatchMessageW; reinterpreting their lParam as a Box
            // would dereference a non-pointer value.
            if msg.message == WM_USER_INVOKE && msg.hwnd.0.is_null() {
                let task = Box::from_raw(msg.lParam.0 as *mut PostedTask);
                let panic_result = {
                    let env_ref = &mut env;
                    std::panic::catch_unwind(AssertUnwindSafe(|| {
                        let _ = env_ref.call_method(task.runnable.as_obj(), "run", "()V", &[]);
                    }))
                };
                report_task_exception_if_pending(&mut env);
                if let Err(payload) = panic_result {
                    let message = panic_payload_message(payload);
                    report_dispatcher_error(
                        &mut env,
                        &format!("WebView2 dispatcher: Rust panic in task glue: {}", message),
                    );
                }
                continue;
            }
            let _ = TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_postTaskNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    runnable: JObject<'_>,
    target_tid: jlong,
) {
    if target_tid == 0 {
        // Win32 would route the message to the calling thread which is never
        // what we want. Drop the task instead of corrupting the caller's queue.
        return;
    }
    let Ok(runnable) = env.new_global_ref(runnable) else {
        return;
    };
    let task = Box::new(PostedTask { runnable });
    let raw = Box::into_raw(task);
    unsafe {
        if PostThreadMessageW(
            target_tid as u32,
            WM_USER_INVOKE,
            WPARAM(0),
            LPARAM(raw as isize),
        )
        .is_err()
        {
            // Reclaim the box if posting failed so we do not leak the global ref.
            drop(Box::from_raw(raw));
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_impl_windows_WinWebView2Bridge_stopMessageLoopNative(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    target_tid: jlong,
) {
    unsafe {
        let _ = PostThreadMessageW(target_tid as u32, WM_QUIT, WPARAM(0), LPARAM(0));
    }
}

struct PostedTask {
    runnable: GlobalRef,
}

fn report_task_exception_if_pending(env: &mut JNIEnv<'_>) {
    if !env.exception_check().unwrap_or(false) {
        return;
    }
    let throwable = match env.exception_occurred() {
        Ok(t) => t,
        Err(_) => {
            let _ = env.exception_clear();
            return;
        }
    };
    let _ = env.exception_clear();
    let _ = env.call_static_method(
        "com/intellij/ui/webview/impl/windows/WinWebView2Bridge",
        "reportTaskException",
        "(Ljava/lang/Throwable;)V",
        &[(&throwable).into()],
    );
    // If the reporter itself threw, clear so we don't leak the pending exception
    // back into the next iteration of the dispatcher loop.
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

fn report_dispatcher_error(env: &mut JNIEnv<'_>, message: &str) {
    let java_message = match env.new_string(message) {
        Ok(s) => s,
        Err(_) => return,
    };
    let _ = env.call_static_method(
        "com/intellij/ui/webview/impl/windows/WinWebView2Bridge",
        "reportDispatcherError",
        "(Ljava/lang/String;)V",
        &[(&java_message).into()],
    );
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

fn panic_payload_message(payload: Box<dyn std::any::Any + Send>) -> String {
    if let Some(s) = payload.downcast_ref::<&'static str>() {
        return (*s).to_owned();
    }
    if let Some(s) = payload.downcast_ref::<String>() {
        return s.clone();
    }
    "<non-string panic payload>".to_owned()
}

fn create_native(
    env: &mut JNIEnv<'_>,
    parent_hwnd: jlong,
    user_data_dir: JString<'_>,
    document_start_script: JString<'_>,
    callbacks: JObject<'_>,
) -> BridgeResult<jlong> {
    unsafe {
        let _ = CoInitializeEx(None, COINIT_APARTMENTTHREADED).ok();
    }

    let callbacks = Rc::new(JavaCallbacks {
        vm: env.get_java_vm().map_err(format_jni_error)?,
        object: env.new_global_ref(callbacks).map_err(format_jni_error)?,
    });
    let parent = HWND(parent_hwnd as *mut c_void);
    let hwnd = create_container_hwnd(parent, callbacks.clone())?;
    let user_data_dir = jstring_to_string(env, user_data_dir)?;
    let document_start_script = jstring_to_string(env, document_start_script)?;
    let document_start_scripts = if document_start_script.is_empty() {
        Vec::new()
    } else {
        vec![document_start_script]
    };

    let native = Rc::new(RefCell::new(NativeWebView {
        handle: 0,
        parent,
        hwnd,
        controller: None,
        webview: None,
        controller_completed_handler: None,
        controller_create_started_at: None,
        last_navigation_requested_at: None,
        navigation_timings: HashMap::new(),
        unidentified_navigation_timing: None,
        add_script_handlers: Vec::new(),
        execute_script_handlers: Vec::new(),
        dev_tools_handlers: Vec::new(),
        next_script_handler_id: 0,
        document_start_scripts,
        web_message_token: EventRegistrationToken::default(),
        web_resource_requested_token: None,
        accelerator_key_pressed_token: None,
        got_focus_token: None,
        callbacks,
        destroyed: false,
        visible: false,
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        scale: 1.0,
    }));

    let raw = Rc::into_raw(native.clone()) as jlong;
    native.borrow_mut().handle = raw;
    if let Err(message) = ensure_shared_environment(native.clone(), user_data_dir) {
        native.borrow_mut().destroy();
        unsafe {
            drop(Rc::from_raw(raw as *const RefCell<NativeWebView>));
        }
        return Err(message);
    }
    Ok(raw)
}

fn ensure_shared_environment(native: NativeHandle, user_data_dir: String) -> BridgeResult<()> {
    let key = EnvironmentKey::new(user_data_dir);
    let action = SHARED_ENVIRONMENT_MANAGER
        .with(|manager| manager.borrow_mut().ensure_environment(key, native.clone()))?;
    match action {
        SharedEnvironmentAction::None => Ok(()),
        SharedEnvironmentAction::CreateController {
            environment,
            generation,
        } => begin_create_controller(native, environment, generation),
        SharedEnvironmentAction::StartEnvironment {
            user_data_dir,
            generation,
            handler,
        } => start_shared_environment_creation(user_data_dir, generation, handler),
    }
}

fn start_shared_environment_creation(
    user_data_dir: String,
    generation: u64,
    handler: ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler,
) -> BridgeResult<()> {
    let options = CoreWebView2EnvironmentOptions::default();
    configure_asset_custom_scheme(&options);
    let options = ICoreWebView2EnvironmentOptions::from(options);
    unsafe {
        if let Err(error) =
            options.SetAdditionalBrowserArguments(w!("--disable-features=ElasticOverscroll"))
        {
            SHARED_ENVIRONMENT_MANAGER.with(|manager| {
                manager.borrow_mut().clear_start_failure(generation);
            });
            return Err(format_windows_error(error));
        }
    }
    let user_data_dir = HSTRING::from(user_data_dir);
    unsafe {
        if let Err(error) = CreateCoreWebView2EnvironmentWithOptions(
            PCWSTR::null(),
            &user_data_dir,
            &options,
            &handler,
        ) {
            SHARED_ENVIRONMENT_MANAGER.with(|manager| {
                manager.borrow_mut().clear_start_failure(generation);
            });
            return Err(format_windows_error(error));
        }
    }
    Ok(())
}

fn configure_asset_custom_scheme(options: &CoreWebView2EnvironmentOptions) {
    let registration =
        CoreWebView2CustomSchemeRegistration::new(WEBVIEW_ASSET_CUSTOM_SCHEME.to_string());
    unsafe {
        // Keep the fixed authority (`ij-webview-asset://assets/...`): WebView2 ES modules need
        // a non-opaque custom-scheme origin, and per-view routing is done by WebResourceRequested.
        registration.set_has_authority_component(true);
        registration.set_treat_as_secure(true);
    }
    let registration: ICoreWebView2CustomSchemeRegistration = registration.into();
    unsafe {
        options.set_scheme_registrations(vec![Some(registration)]);
    }
}

fn create_shared_environment_completed_handler(
    generation: u64,
) -> ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler {
    CreateCoreWebView2EnvironmentCompletedHandler::create(Box::new(
        move |error_code, environment| {
            if let Err(error) = error_code {
                fail_shared_environment_creation(generation, format_windows_error(error));
                return Ok(());
            }

            let Some(environment) = environment else {
                fail_shared_environment_creation(
                    generation,
                    "WebView2 environment callback returned null".to_string(),
                );
                return Ok(());
            };

            let environment_started_at = shared_environment_started_at(generation);
            let waiters = SHARED_ENVIRONMENT_MANAGER.with(|manager| {
                manager
                    .borrow_mut()
                    .complete_environment_success(generation, environment.clone())
            });
            let diagnostic_target = waiters
                .iter()
                .find(|view| !is_native_destroyed(view))
                .cloned();
            if let Some(native) = &diagnostic_target {
                log_environment_metadata(&environment, native);
                if let Some(started_at) = environment_started_at {
                    emit_perf_diagnostic(
                        native,
                        "perf.webview2.environment.create",
                        started_at,
                        vec![("generation", generation.to_string())],
                    );
                }
            }
            if let Err(message) = attach_environment_diagnostics(&environment, generation) {
                if let Some(native) = &diagnostic_target {
                    emit_diagnostic(
                        native,
                        DIAGNOSTIC_WARN,
                        "diagnostics.attach-environment-failed",
                        message,
                        String::new(),
                    );
                }
            }

            for native in waiters {
                if is_native_destroyed(&native) || !is_shared_environment_current(generation) {
                    continue;
                }
                if let Err(message) =
                    begin_create_controller(native.clone(), environment.clone(), generation)
                {
                    fail_create(&native, message);
                }
            }
            Ok(())
        },
    ))
}

fn fail_shared_environment_creation(generation: u64, message: String) {
    let waiters = SHARED_ENVIRONMENT_MANAGER.with(|manager| {
        manager
            .borrow_mut()
            .complete_environment_failure(generation)
    });
    for native in waiters {
        if !is_native_destroyed(&native) {
            fail_create(&native, message.clone());
        }
    }
}

fn begin_create_controller(
    native: NativeHandle,
    environment: ICoreWebView2Environment,
    generation: u64,
) -> BridgeResult<()> {
    if is_native_destroyed(&native) {
        return Ok(());
    }
    if !register_shared_environment_active_view(generation, native.clone()) {
        return Ok(());
    }
    let hwnd = native.borrow().hwnd;
    let environment_for_callback = environment.clone();
    let native_for_callback = native.clone();
    let handler = CreateCoreWebView2ControllerCompletedHandler::create(Box::new(
        move |error_code, controller| {
            let controller_started_at = if let Ok(mut view) = native_for_callback.try_borrow_mut() {
                view.controller_completed_handler = None;
                view.controller_create_started_at.take()
            } else {
                None
            };
            if is_native_destroyed(&native_for_callback)
                || !is_shared_environment_current(generation)
            {
                return Ok(());
            }
            if let Err(error) = error_code {
                fail_create(&native_for_callback, format_windows_error(error));
                return Ok(());
            }

            let Some(controller) = controller else {
                fail_create(
                    &native_for_callback,
                    "WebView2 controller callback returned null".to_string(),
                );
                return Ok(());
            };

            if let Some(started_at) = controller_started_at {
                emit_perf_diagnostic(
                    &native_for_callback,
                    "perf.webview2.controller.create",
                    started_at,
                    vec![("generation", generation.to_string())],
                );
            }
            match finish_create(
                native_for_callback.clone(),
                environment_for_callback.clone(),
                controller,
                generation,
            ) {
                Ok(()) => {}
                Err(message) => fail_create(&native_for_callback, message),
            }
            Ok(())
        },
    ));
    {
        let mut view = native.borrow_mut();
        view.controller_completed_handler = Some(handler.clone());
        view.controller_create_started_at = Some(Instant::now());
    }
    unsafe {
        if let Err(error) = environment.CreateCoreWebView2Controller(hwnd, &handler) {
            unregister_shared_environment_view(native_handle(&native));
            if let Ok(mut view) = native.try_borrow_mut() {
                view.controller_create_started_at = None;
            }
            return Err(format_windows_error(error));
        }
    }
    Ok(())
}

fn finish_create(
    native: NativeHandle,
    environment: ICoreWebView2Environment,
    controller: ICoreWebView2Controller,
    generation: u64,
) -> BridgeResult<()> {
    let finish_started_at = Instant::now();
    let webview = measure_perf(
        &native,
        "perf.webview2.finish.core-webview2",
        vec![("generation", generation.to_string())],
        || unsafe { controller.CoreWebView2().map_err(format_windows_error) },
    )?;

    measure_perf(
        &native,
        "perf.webview2.finish.settings",
        vec![("generation", generation.to_string())],
        || configure_webview_application_settings(&webview),
    )?;

    let script_count = native
        .try_borrow()
        .map(|view| view.document_start_scripts.len())
        .unwrap_or_default();
    measure_perf(
        &native,
        "perf.webview2.finish.document-start-scripts",
        vec![
            ("generation", generation.to_string()),
            ("scriptCount", script_count.to_string()),
        ],
        || install_document_start_scripts(&webview, native.clone()),
    )?;

    let token = measure_perf(
        &native,
        "perf.webview2.finish.ipc-handler",
        vec![("generation", generation.to_string())],
        || attach_ipc_handler(&webview, native.clone()),
    )?;

    let web_resource_token = measure_perf(
        &native,
        "perf.webview2.finish.resource-handler",
        vec![("generation", generation.to_string())],
        || attach_web_resource_requested_handler(&environment, &webview, native.clone()),
    )?;

    let accelerator_token = measure_perf(
        &native,
        "perf.webview2.finish.accelerator-handler",
        vec![("generation", generation.to_string())],
        || attach_accelerator_key_handler(&controller, native.clone()),
    )?;

    let got_focus_token = measure_perf(
        &native,
        "perf.webview2.finish.focus-handler",
        vec![("generation", generation.to_string())],
        || attach_got_focus_handler(&controller, native.clone()),
    )?;

    measure_perf(
        &native,
        "perf.webview2.finish.diagnostics",
        vec![("generation", generation.to_string())],
        || {
            if let Err(message) = attach_webview_diagnostics(&webview, native.clone()) {
                emit_diagnostic(
                    &native,
                    DIAGNOSTIC_WARN,
                    "diagnostics.attach-webview-failed",
                    message,
                    String::new(),
                );
            }
            Ok(())
        },
    )?;

    let (callbacks, handle, hwnd, controller, visible, x, y, width, height, scale) = {
        let mut view = native.borrow_mut();
        if view.destroyed || !is_shared_environment_current(generation) {
            return Ok(());
        }

        view.web_message_token = token;
        view.web_resource_requested_token = Some(web_resource_token);
        view.accelerator_key_pressed_token = Some(accelerator_token);
        view.got_focus_token = Some(got_focus_token);
        view.controller = Some(controller);
        view.webview = Some(webview);
        (
            view.callbacks.clone(),
            view.handle,
            view.hwnd,
            view.controller.clone(),
            view.visible,
            view.x,
            view.y,
            view.width,
            view.height,
            view.scale,
        )
    };

    apply_bounds_values(hwnd, controller.as_ref(), x, y, width, height, scale)?;
    unsafe {
        if let Some(controller) = &controller {
            controller
                .SetIsVisible(visible)
                .map_err(format_windows_error)?;
        }
        let _ = ShowWindow(hwnd, if visible { SW_SHOW } else { SW_HIDE });
    }

    callbacks.on_created(handle);
    emit_perf_diagnostic(
        &native,
        "perf.webview2.finish.total",
        finish_started_at,
        vec![
            ("generation", generation.to_string()),
            ("handle", handle.to_string()),
        ],
    );
    Ok(())
}

fn configure_webview_application_settings(webview: &ICoreWebView2) -> BridgeResult<()> {
    let settings = unsafe { webview.Settings().map_err(format_windows_error)? };
    unsafe {
        settings
            .SetIsScriptEnabled(true)
            .map_err(format_windows_error)?;
        settings
            .SetIsWebMessageEnabled(true)
            .map_err(format_windows_error)?;
        settings
            .SetAreDefaultScriptDialogsEnabled(false)
            .map_err(format_windows_error)?;
        settings
            .SetIsStatusBarEnabled(false)
            .map_err(format_windows_error)?;
        // This blocks user entry points only; host code can still call OpenDevToolsWindow.
        settings
            .SetAreDevToolsEnabled(false)
            .map_err(format_windows_error)?;
        settings
            .SetAreDefaultContextMenusEnabled(false)
            .map_err(format_windows_error)?;
        settings
            .SetAreHostObjectsAllowed(false)
            .map_err(format_windows_error)?;
        settings
            .SetIsZoomControlEnabled(false)
            .map_err(format_windows_error)?;
        settings
            .SetIsBuiltInErrorPageEnabled(false)
            .map_err(format_windows_error)?;
    }

    if let Ok(settings3) = settings.cast::<ICoreWebView2Settings3>() {
        unsafe {
            settings3
                .SetAreBrowserAcceleratorKeysEnabled(false)
                .map_err(format_windows_error)?;
        }
    }
    if let Ok(settings4) = settings.cast::<ICoreWebView2Settings4>() {
        unsafe {
            settings4
                .SetIsGeneralAutofillEnabled(false)
                .map_err(format_windows_error)?;
            settings4
                .SetIsPasswordAutosaveEnabled(false)
                .map_err(format_windows_error)?;
        }
    }
    if let Ok(settings6) = settings.cast::<ICoreWebView2Settings6>() {
        unsafe {
            settings6
                .SetIsSwipeNavigationEnabled(false)
                .map_err(format_windows_error)?;
        }
    }

    Ok(())
}

fn emit_diagnostic(native: &NativeHandle, level: jint, event: &str, message: String, data: String) {
    let callbacks = match native.try_borrow() {
        Ok(view) => view.callbacks.clone(),
        Err(_) => return,
    };
    callbacks.on_native_diagnostic(level, event, message, data);
}

fn emit_perf_diagnostic(
    native: &NativeHandle,
    event: &str,
    started_at: Instant,
    mut data: Vec<(&str, String)>,
) {
    let elapsed = started_at.elapsed();
    data.push(("elapsedMs", elapsed.as_millis().to_string()));
    emit_diagnostic(
        native,
        DIAGNOSTIC_TRACE,
        event,
        "WebView2 perf timing".to_string(),
        diagnostic_data(data),
    );
}

fn measure_perf<T>(
    native: &NativeHandle,
    event: &str,
    data: Vec<(&str, String)>,
    action: impl FnOnce() -> BridgeResult<T>,
) -> BridgeResult<T> {
    let started_at = Instant::now();
    let result = action();
    if result.is_ok() {
        emit_perf_diagnostic(native, event, started_at, data);
    }
    result
}

fn record_navigation_start(
    native: &NativeHandle,
    navigation_id: Option<u64>,
    data: &mut Vec<(&str, String)>,
) {
    let now = Instant::now();
    if let Ok(mut view) = native.try_borrow_mut() {
        let timing = NavigationTiming {
            requested_at: view.last_navigation_requested_at,
            started_at: now,
        };
        if let Some(requested_at) = timing.requested_at {
            append_elapsed_ms(data, "sinceLoadCallMs", requested_at);
        }
        if let Some(navigation_id) = navigation_id {
            view.navigation_timings.insert(navigation_id, timing);
        } else {
            view.unidentified_navigation_timing = Some(timing);
        }
    }
}

fn append_navigation_progress_timings(
    native: &NativeHandle,
    navigation_id: Option<u64>,
    data: &mut Vec<(&str, String)>,
) {
    if let Ok(view) = native.try_borrow() {
        if let Some(timing) = current_navigation_timing(&view, navigation_id) {
            append_navigation_timing(data, timing);
        }
    }
}

fn complete_navigation_timings(
    native: &NativeHandle,
    navigation_id: Option<u64>,
    data: &mut Vec<(&str, String)>,
) {
    if let Ok(mut view) = native.try_borrow_mut() {
        let timing = if let Some(navigation_id) = navigation_id {
            view.navigation_timings.remove(&navigation_id)
        } else if view.navigation_timings.len() == 1 {
            let navigation_id = *view.navigation_timings.keys().next().unwrap();
            view.navigation_timings.remove(&navigation_id)
        } else {
            view.unidentified_navigation_timing.take()
        };
        if let Some(timing) = timing {
            append_navigation_timing(data, timing);
        }
        if view.navigation_timings.is_empty() && view.unidentified_navigation_timing.is_none() {
            view.last_navigation_requested_at = None;
        }
    }
}

fn current_navigation_timing(
    view: &NativeWebView,
    navigation_id: Option<u64>,
) -> Option<NavigationTiming> {
    if let Some(navigation_id) = navigation_id {
        return view.navigation_timings.get(&navigation_id).copied();
    }
    if view.navigation_timings.len() == 1 {
        return view.navigation_timings.values().next().copied();
    }
    view.unidentified_navigation_timing
}

fn append_navigation_timing(data: &mut Vec<(&str, String)>, timing: NavigationTiming) {
    if let Some(requested_at) = timing.requested_at {
        append_elapsed_ms(data, "sinceLoadCallMs", requested_at);
    }
    append_elapsed_ms(data, "sinceNavigationStartMs", timing.started_at);
}

fn append_elapsed_ms(data: &mut Vec<(&str, String)>, name: &'static str, started_at: Instant) {
    data.push((name, started_at.elapsed().as_millis().to_string()));
}

fn log_environment_metadata(environment: &ICoreWebView2Environment, native: &NativeHandle) {
    let mut data = Vec::new();
    if let Some(version) = unsafe { get_pwstr(|value| environment.BrowserVersionString(value)) } {
        data.push(("browserVersion", version));
    }
    if let Ok(environment7) = environment.cast::<ICoreWebView2Environment7>() {
        if let Some(user_data_folder) =
            unsafe { get_pwstr(|value| environment7.UserDataFolder(value)) }
        {
            data.push(("userDataFolder", user_data_folder));
        }
    }
    if let Ok(environment11) = environment.cast::<ICoreWebView2Environment11>() {
        if let Some(failure_report_folder) =
            unsafe { get_pwstr(|value| environment11.FailureReportFolderPath(value)) }
        {
            data.push(("failureReportFolder", failure_report_folder));
        }
    }
    emit_diagnostic(
        native,
        DIAGNOSTIC_INFO,
        "runtime.environment",
        "WebView2 environment created".to_string(),
        diagnostic_data(data),
    );
}

fn attach_environment_diagnostics(
    environment: &ICoreWebView2Environment,
    generation: u64,
) -> BridgeResult<()> {
    let version_handler = NewBrowserVersionAvailableEventHandler::create(Box::new(move |_, _| {
        emit_shared_environment_diagnostic(
            generation,
            DIAGNOSTIC_INFO,
            "runtime.new-browser-version-available",
            "A new WebView2 runtime version is available".to_string(),
            String::new(),
        );
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        environment
            .add_NewBrowserVersionAvailable(&version_handler, &mut token)
            .map_err(format_windows_error)?;
    }

    if let Ok(environment5) = environment.cast::<ICoreWebView2Environment5>() {
        let handler = BrowserProcessExitedEventHandler::create(Box::new(move |_, args| {
            if let Some(args) = args {
                handle_browser_process_exited(generation, &args);
            }
            Ok(())
        }));
        let mut token = EventRegistrationToken::default();
        unsafe {
            environment5
                .add_BrowserProcessExited(&handler, &mut token)
                .map_err(format_windows_error)?;
        }
    }

    if let Ok(environment8) = environment.cast::<ICoreWebView2Environment8>() {
        log_process_infos(&environment8, generation);
        let environment_for_processes = environment8.clone();
        let handler = ProcessInfosChangedEventHandler::create(Box::new(move |_, _| {
            log_process_infos(&environment_for_processes, generation);
            Ok(())
        }));
        let mut token = EventRegistrationToken::default();
        unsafe {
            environment8
                .add_ProcessInfosChanged(&handler, &mut token)
                .map_err(format_windows_error)?;
        }
    }

    Ok(())
}

fn attach_webview_diagnostics(webview: &ICoreWebView2, native: NativeHandle) -> BridgeResult<()> {
    attach_process_failed_handler(webview, native.clone())?;
    attach_navigation_handlers(webview, native.clone())?;
    attach_trace_webview_handlers(webview, native)?;
    Ok(())
}

fn attach_process_failed_handler(
    webview: &ICoreWebView2,
    native: NativeHandle,
) -> BridgeResult<()> {
    let handler = ProcessFailedEventHandler::create(Box::new(move |_, args| {
        if let Some(args) = args {
            handle_process_failed(&native, &args);
        }
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_ProcessFailed(&handler, &mut token)
            .map_err(format_windows_error)?;
    }
    Ok(())
}

fn attach_navigation_handlers(webview: &ICoreWebView2, native: NativeHandle) -> BridgeResult<()> {
    let native_for_start = native.clone();
    let starting_handler = NavigationStartingEventHandler::create(Box::new(move |_, args| {
        if let Some(args) = args {
            let mut data = Vec::new();
            if let Some(uri) = unsafe { get_pwstr(|value| args.Uri(value)) } {
                data.push(("uri", uri));
            }
            let navigation_id = unsafe { get_u64(|value| args.NavigationId(value)) };
            if let Some(navigation_id) = navigation_id {
                data.push(("navigationId", navigation_id.to_string()));
            }
            record_navigation_start(&native_for_start, navigation_id, &mut data);
            emit_diagnostic(
                &native_for_start,
                DIAGNOSTIC_DEBUG,
                "navigation.starting",
                "WebView2 navigation starting".to_string(),
                diagnostic_data(data),
            );
        }
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_NavigationStarting(&starting_handler, &mut token)
            .map_err(format_windows_error)?;
    }

    let native_for_completed = native.clone();
    let completed_handler = NavigationCompletedEventHandler::create(Box::new(move |_, args| {
        if let Some(args) = args {
            handle_navigation_completed(&native_for_completed, &args);
        }
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_NavigationCompleted(&completed_handler, &mut token)
            .map_err(format_windows_error)?;
    }

    let native_for_content = native.clone();
    let content_handler = ContentLoadingEventHandler::create(Box::new(move |_, args| {
        if let Some(args) = args {
            let mut data = Vec::new();
            let navigation_id = unsafe { get_u64(|value| args.NavigationId(value)) };
            if let Some(navigation_id) = navigation_id {
                data.push(("navigationId", navigation_id.to_string()));
            }
            if let Some(is_error_page) = unsafe { get_bool(|value| args.IsErrorPage(value)) } {
                data.push(("isErrorPage", is_error_page.to_string()));
            }
            append_navigation_progress_timings(&native_for_content, navigation_id, &mut data);
            emit_diagnostic(
                &native_for_content,
                DIAGNOSTIC_DEBUG,
                "navigation.content-loading",
                "WebView2 content loading".to_string(),
                diagnostic_data(data),
            );
        }
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_ContentLoading(&content_handler, &mut token)
            .map_err(format_windows_error)?;
    }

    if let Ok(webview2) = webview.cast::<ICoreWebView2_2>() {
        let native_for_dom = native.clone();
        let handler = DOMContentLoadedEventHandler::create(Box::new(move |_, args| {
            if let Some(args) = args {
                let mut data = Vec::new();
                let navigation_id = unsafe { get_u64(|value| args.NavigationId(value)) };
                if let Some(navigation_id) = navigation_id {
                    data.push(("navigationId", navigation_id.to_string()));
                }
                append_navigation_progress_timings(&native_for_dom, navigation_id, &mut data);
                emit_diagnostic(
                    &native_for_dom,
                    DIAGNOSTIC_DEBUG,
                    "navigation.dom-content-loaded",
                    "WebView2 DOMContentLoaded".to_string(),
                    diagnostic_data(data),
                );
            }
            Ok(())
        }));
        let mut token = EventRegistrationToken::default();
        unsafe {
            webview2
                .add_DOMContentLoaded(&handler, &mut token)
                .map_err(format_windows_error)?;
        }
    }

    let native_for_source = native.clone();
    let source_handler = SourceChangedEventHandler::create(Box::new(move |_, args| {
        if let Some(args) = args {
            let mut data = Vec::new();
            if let Some(is_new_document) = unsafe { get_bool(|value| args.IsNewDocument(value)) } {
                data.push(("isNewDocument", is_new_document.to_string()));
            }
            append_navigation_progress_timings(&native_for_source, None, &mut data);
            emit_diagnostic(
                &native_for_source,
                DIAGNOSTIC_DEBUG,
                "navigation.source-changed",
                "WebView2 source changed".to_string(),
                diagnostic_data(data),
            );
        }
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_SourceChanged(&source_handler, &mut token)
            .map_err(format_windows_error)?;
    }

    let native_for_history = native.clone();
    let history_handler = HistoryChangedEventHandler::create(Box::new(move |_, _| {
        emit_diagnostic(
            &native_for_history,
            DIAGNOSTIC_DEBUG,
            "navigation.history-changed",
            "WebView2 history changed".to_string(),
            String::new(),
        );
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_HistoryChanged(&history_handler, &mut token)
            .map_err(format_windows_error)?;
    }

    Ok(())
}

fn attach_trace_webview_handlers(
    webview: &ICoreWebView2,
    native: NativeHandle,
) -> BridgeResult<()> {
    if let Ok(webview2) = webview.cast::<ICoreWebView2_2>() {
        let native_for_resource = native.clone();
        let resource_handler =
            WebResourceResponseReceivedEventHandler::create(Box::new(move |_, args| {
                if let Some(args) = args {
                    handle_web_resource_response_received(&native_for_resource, &args);
                }
                Ok(())
            }));
        let mut token = EventRegistrationToken::default();
        unsafe {
            webview2
                .add_WebResourceResponseReceived(&resource_handler, &mut token)
                .map_err(format_windows_error)?;
        }
    }

    let native_for_permission = native.clone();
    let permission_handler = PermissionRequestedEventHandler::create(Box::new(move |_, args| {
        if let Some(args) = args {
            let mut data = Vec::new();
            if let Some(uri) = unsafe { get_pwstr(|value| args.Uri(value)) } {
                data.push(("uri", uri));
            }
            if let Some(kind) = unsafe { get_permission_kind(|value| args.PermissionKind(value)) } {
                data.push(("kind", permission_kind_name(kind).to_string()));
                data.push(("kindCode", kind.0.to_string()));
            }
            if let Some(is_user_initiated) =
                unsafe { get_bool(|value| args.IsUserInitiated(value)) }
            {
                data.push(("isUserInitiated", is_user_initiated.to_string()));
            }
            emit_diagnostic(
                &native_for_permission,
                DIAGNOSTIC_TRACE,
                "security.permission-requested",
                "WebView2 permission requested".to_string(),
                diagnostic_data(data),
            );
        }
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_PermissionRequested(&permission_handler, &mut token)
            .map_err(format_windows_error)?;
    }

    let native_for_new_window = native.clone();
    let new_window_handler = NewWindowRequestedEventHandler::create(Box::new(move |_, args| {
        if let Some(args) = args {
            let mut data = Vec::new();
            if let Some(uri) = unsafe { get_pwstr(|value| args.Uri(value)) } {
                data.push(("uri", uri));
            }
            if let Some(is_user_initiated) =
                unsafe { get_bool(|value| args.IsUserInitiated(value)) }
            {
                data.push(("isUserInitiated", is_user_initiated.to_string()));
            }
            emit_diagnostic(
                &native_for_new_window,
                DIAGNOSTIC_TRACE,
                "security.new-window-requested",
                "WebView2 new window requested".to_string(),
                diagnostic_data(data),
            );
        }
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_NewWindowRequested(&new_window_handler, &mut token)
            .map_err(format_windows_error)?;
    }

    if let Ok(webview10) = webview.cast::<ICoreWebView2_10>() {
        let native_for_auth = native.clone();
        let handler = BasicAuthenticationRequestedEventHandler::create(Box::new(move |_, args| {
            if let Some(args) = args {
                let mut data = Vec::new();
                if let Some(uri) = unsafe { get_pwstr(|value| args.Uri(value)) } {
                    data.push(("uri", uri));
                }
                if let Some(challenge) = unsafe { get_pwstr(|value| args.Challenge(value)) } {
                    data.push(("challenge", challenge));
                }
                emit_diagnostic(
                    &native_for_auth,
                    DIAGNOSTIC_TRACE,
                    "security.basic-auth-requested",
                    "WebView2 basic authentication requested".to_string(),
                    diagnostic_data(data),
                );
            }
            Ok(())
        }));
        let mut token = EventRegistrationToken::default();
        unsafe {
            webview10
                .add_BasicAuthenticationRequested(&handler, &mut token)
                .map_err(format_windows_error)?;
        }
    }

    if let Ok(webview14) = webview.cast::<ICoreWebView2_14>() {
        let native_for_cert = native.clone();
        let handler =
            ServerCertificateErrorDetectedEventHandler::create(Box::new(move |_, args| {
                if let Some(args) = args {
                    let mut data = Vec::new();
                    if let Some(uri) = unsafe { get_pwstr(|value| args.RequestUri(value)) } {
                        data.push(("uri", uri));
                    }
                    if let Some(error_status) =
                        unsafe { get_web_error_status(|value| args.ErrorStatus(value)) }
                    {
                        data.push(("errorStatus", format!("{error_status:?}")));
                        data.push(("errorStatusCode", error_status.0.to_string()));
                    }
                    emit_diagnostic(
                        &native_for_cert,
                        DIAGNOSTIC_TRACE,
                        "security.server-certificate-error",
                        "WebView2 server certificate error detected".to_string(),
                        diagnostic_data(data),
                    );
                }
                Ok(())
            }));
        let mut token = EventRegistrationToken::default();
        unsafe {
            webview14
                .add_ServerCertificateErrorDetected(&handler, &mut token)
                .map_err(format_windows_error)?;
        }
    }

    Ok(())
}

fn handle_navigation_completed(
    native: &NativeHandle,
    args: &ICoreWebView2NavigationCompletedEventArgs,
) {
    let mut data = Vec::new();
    let is_success = unsafe { get_bool(|value| args.IsSuccess(value)) }.unwrap_or(false);
    data.push(("isSuccess", is_success.to_string()));
    let navigation_id = unsafe { get_u64(|value| args.NavigationId(value)) };
    if let Some(navigation_id) = navigation_id {
        data.push(("navigationId", navigation_id.to_string()));
    }
    if let Some(web_error_status) =
        unsafe { get_web_error_status(|value| args.WebErrorStatus(value)) }
    {
        data.push(("webErrorStatus", format!("{web_error_status:?}")));
        data.push(("webErrorStatusCode", web_error_status.0.to_string()));
    }
    if let Ok(args2) = args.cast::<ICoreWebView2NavigationCompletedEventArgs2>() {
        if let Some(http_status_code) = unsafe { get_i32(|value| args2.HttpStatusCode(value)) } {
            data.push(("httpStatusCode", http_status_code.to_string()));
        }
    }
    complete_navigation_timings(native, navigation_id, &mut data);
    emit_diagnostic(
        native,
        if is_success {
            DIAGNOSTIC_INFO
        } else {
            DIAGNOSTIC_WARN
        },
        "navigation.completed",
        if is_success {
            "WebView2 navigation completed".to_string()
        } else {
            "WebView2 navigation failed".to_string()
        },
        diagnostic_data(data),
    );
}

fn handle_web_resource_response_received(
    native: &NativeHandle,
    args: &ICoreWebView2WebResourceResponseReceivedEventArgs,
) {
    let mut data = Vec::new();
    if let Ok(request) = unsafe { args.Request() } {
        if let Some(uri) = unsafe { get_pwstr(|value| request.Uri(value)) } {
            data.push(("uri", uri));
        }
        if let Some(method) = unsafe { get_pwstr(|value| request.Method(value)) } {
            data.push(("method", method));
        }
    }
    if let Ok(response) = unsafe { args.Response() } {
        if let Some(status_code) = unsafe { get_i32(|value| response.StatusCode(value)) } {
            data.push(("statusCode", status_code.to_string()));
        }
        if let Some(reason_phrase) = unsafe { get_pwstr(|value| response.ReasonPhrase(value)) } {
            data.push(("reasonPhrase", reason_phrase));
        }
    }
    emit_diagnostic(
        native,
        DIAGNOSTIC_TRACE,
        "resource.response-received",
        "WebView2 resource response received".to_string(),
        diagnostic_data(data),
    );
}

fn handle_process_failed(native: &NativeHandle, args: &ICoreWebView2ProcessFailedEventArgs) {
    let Some(kind) = (unsafe { get_process_failed_kind(|value| args.ProcessFailedKind(value)) })
    else {
        emit_diagnostic(
            native,
            DIAGNOSTIC_ERROR,
            "process-failed.fatal",
            "WebView2 process failed without kind".to_string(),
            String::new(),
        );
        return;
    };
    let mut data = vec![
        ("kind", process_failed_kind_name(kind).to_string()),
        ("kindCode", kind.0.to_string()),
    ];
    let mut reason = None;
    if let Ok(args2) = args.cast::<ICoreWebView2ProcessFailedEventArgs2>() {
        reason = unsafe { get_process_failed_reason(|value| args2.Reason(value)) };
        if let Some(reason) = reason {
            data.push(("reason", process_failed_reason_name(reason).to_string()));
            data.push(("reasonCode", reason.0.to_string()));
        }
        if let Some(exit_code) = unsafe { get_i32(|value| args2.ExitCode(value)) } {
            data.push(("exitCode", exit_code.to_string()));
        }
        if let Some(description) = unsafe { get_pwstr(|value| args2.ProcessDescription(value)) } {
            data.push(("processDescription", description));
        }
    }
    if let Ok(args3) = args.cast::<ICoreWebView2ProcessFailedEventArgs3>() {
        if let Some(path) = unsafe { get_pwstr(|value| args3.FailureSourceModulePath(value)) } {
            data.push(("failureSourceModulePath", path));
        }
    }

    let event = match (kind, reason) {
        (COREWEBVIEW2_PROCESS_FAILED_KIND_RENDER_PROCESS_UNRESPONSIVE, _) => {
            "process-failed.unresponsive"
        }
        (_, Some(COREWEBVIEW2_PROCESS_FAILED_REASON_PROFILE_DELETED)) => "process-failed.fatal",
        (COREWEBVIEW2_PROCESS_FAILED_KIND_BROWSER_PROCESS_EXITED, _)
        | (COREWEBVIEW2_PROCESS_FAILED_KIND_RENDER_PROCESS_EXITED, _)
        | (COREWEBVIEW2_PROCESS_FAILED_KIND_FRAME_RENDER_PROCESS_EXITED, _) => {
            "process-failed.fatal"
        }
        _ => "process-failed.nonfatal",
    };
    emit_diagnostic(
        native,
        DIAGNOSTIC_ERROR,
        event,
        format!(
            "WebView2 process failed: {}",
            process_failed_kind_name(kind)
        ),
        diagnostic_data(data),
    );
}

fn handle_browser_process_exited(
    generation: u64,
    args: &ICoreWebView2BrowserProcessExitedEventArgs,
) {
    let mut data = Vec::new();
    let exit_kind = unsafe { get_browser_exit_kind(|value| args.BrowserProcessExitKind(value)) };
    if let Some(exit_kind) = exit_kind {
        data.push(("exitKind", browser_exit_kind_name(exit_kind).to_string()));
        data.push(("exitKindCode", exit_kind.0.to_string()));
    }
    if let Some(process_id) = unsafe { get_u32(|value| args.BrowserProcessId(value)) } {
        data.push(("processId", process_id.to_string()));
    }
    let failed = exit_kind == Some(COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND_FAILED);
    let event = if failed {
        "browser-process-exited.fatal"
    } else {
        "browser-process-exited"
    };
    let targets = if failed {
        invalidate_shared_environment(generation)
    } else {
        shared_environment_active_views(generation)
    };
    for native in targets {
        emit_diagnostic(
            &native,
            if failed {
                DIAGNOSTIC_ERROR
            } else {
                DIAGNOSTIC_INFO
            },
            event,
            "WebView2 browser process exited".to_string(),
            diagnostic_data(data.clone()),
        );
    }
}

fn log_process_infos(environment: &ICoreWebView2Environment8, generation: u64) {
    let Ok(collection) = (unsafe { environment.GetProcessInfos() }) else {
        return;
    };
    let Some(count) = (unsafe { get_u32(|value| collection.Count(value)) }) else {
        return;
    };
    let mut processes = Vec::new();
    for index in 0..count {
        let Ok(info) = (unsafe { collection.GetValueAtIndex(index) }) else {
            continue;
        };
        let process_id = unsafe { get_i32(|value| info.ProcessId(value)) }
            .map(|value| value.to_string())
            .unwrap_or_else(|| "unknown".to_string());
        let kind = unsafe { get_process_kind(|value| info.Kind(value)) }
            .map(|value| process_kind_name(value).to_string())
            .unwrap_or_else(|| "unknown".to_string());
        processes.push(format!("{process_id}:{kind}"));
    }
    emit_shared_environment_diagnostic(
        generation,
        DIAGNOSTIC_TRACE,
        "runtime.process-infos-changed",
        "WebView2 process info snapshot".to_string(),
        diagnostic_data(vec![
            ("count", count.to_string()),
            ("processes", processes.join(",")),
        ]),
    );
}

fn emit_shared_environment_diagnostic(
    generation: u64,
    level: jint,
    event: &str,
    message: String,
    data: String,
) {
    for native in shared_environment_active_views(generation) {
        emit_diagnostic(&native, level, event, message.clone(), data.clone());
    }
}

fn shared_environment_active_views(generation: u64) -> Vec<NativeHandle> {
    SHARED_ENVIRONMENT_MANAGER.with(|manager| manager.borrow_mut().active_views(generation))
}

fn shared_environment_started_at(generation: u64) -> Option<Instant> {
    SHARED_ENVIRONMENT_MANAGER.with(|manager| manager.borrow().environment_started_at(generation))
}

fn invalidate_shared_environment(generation: u64) -> Vec<NativeHandle> {
    SHARED_ENVIRONMENT_MANAGER
        .with(|manager| manager.borrow_mut().invalidate_environment(generation))
}

fn register_shared_environment_active_view(generation: u64, native: NativeHandle) -> bool {
    SHARED_ENVIRONMENT_MANAGER.with(|manager| {
        manager
            .borrow_mut()
            .register_active_view(generation, native)
    })
}

fn unregister_shared_environment_view(handle: jlong) {
    SHARED_ENVIRONMENT_MANAGER.with(|manager| manager.borrow_mut().unregister_view(handle));
}

fn is_shared_environment_current(generation: u64) -> bool {
    SHARED_ENVIRONMENT_MANAGER.with(|manager| manager.borrow().is_current_generation(generation))
}

fn remove_view_handle(views: &mut Vec<NativeHandle>, handle: jlong) {
    views.retain(|view| native_handle(view) != handle && !is_native_destroyed(view));
}

fn prune_destroyed_views(views: &mut Vec<NativeHandle>) {
    views.retain(|view| !is_native_destroyed(view));
}

fn native_handle(native: &NativeHandle) -> jlong {
    native
        .try_borrow()
        .map(|view| view.handle)
        .unwrap_or_default()
}

fn is_native_destroyed(native: &NativeHandle) -> bool {
    native
        .try_borrow()
        .map(|view| view.destroyed)
        .unwrap_or(false)
}

fn attach_web_resource_requested_handler(
    environment: &ICoreWebView2Environment,
    webview: &ICoreWebView2,
    native: NativeHandle,
) -> BridgeResult<EventRegistrationToken> {
    let environment = environment.clone();
    let native_for_callback = native.clone();
    let handler = WebResourceRequestedEventHandler::create(Box::new(move |_, args| {
        if let Some(args) = args {
            if let Err(message) =
                handle_web_resource_requested(&environment, &native_for_callback, &args)
            {
                if let Ok(view) = native_for_callback.try_borrow() {
                    view.callbacks.on_log(
                        DIAGNOSTIC_WARN,
                        format!("WinWebView2Bridge: asset request failed: {message}"),
                    );
                }
            }
        }
        Ok(())
    }));
    let mut token = EventRegistrationToken::default();
    unsafe {
        add_web_resource_requested_filter(webview, WEBVIEW_ASSET_CUSTOM_SCHEME_FILTER)?;
        add_web_resource_requested_filter(webview, WEBVIEW_ASSET_HTTPS_FILTER)?;
        webview
            .add_WebResourceRequested(&handler, &mut token)
            .map_err(format_windows_error)?;
    }
    Ok(token)
}

fn add_web_resource_requested_filter(webview: &ICoreWebView2, filter: &str) -> BridgeResult<()> {
    unsafe {
        webview.AddWebResourceRequestedFilter(
            &HSTRING::from(filter),
            COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL,
        )
    }
    .map_err(format_windows_error)
}

fn remove_web_resource_requested_filter(webview: &ICoreWebView2, filter: &str) {
    unsafe {
        let _ = webview.RemoveWebResourceRequestedFilter(
            &HSTRING::from(filter),
            COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL,
        );
    }
}

fn handle_web_resource_requested(
    environment: &ICoreWebView2Environment,
    native: &NativeHandle,
    args: &ICoreWebView2WebResourceRequestedEventArgs,
) -> BridgeResult<()> {
    let request = unsafe { args.Request().map_err(format_windows_error)? };
    let mut uri = PWSTR::null();
    unsafe {
        request.Uri(&mut uri).map_err(format_windows_error)?;
    }
    let url = take_pwstr(uri);
    let callbacks = native
        .try_borrow()
        .map_err(|_| "WebView2 state is busy while resolving asset".to_string())?
        .callbacks
        .clone();
    let Some(asset_response) = callbacks.resolve_asset(url)? else {
        return Ok(());
    };
    let response = create_web_resource_response(environment, asset_response)?;
    unsafe {
        args.SetResponse(&response).map_err(format_windows_error)?;
    }
    Ok(())
}

fn set_virtual_host_name_to_folder_mapping(
    webview: &ICoreWebView2,
    host_name: &str,
    folder_path: &str,
) -> BridgeResult<()> {
    let webview3 = webview
        .cast::<ICoreWebView2_3>()
        .map_err(format_windows_error)?;
    unsafe {
        webview3
            .SetVirtualHostNameToFolderMapping(
                &HSTRING::from(host_name),
                &HSTRING::from(folder_path),
                COREWEBVIEW2_HOST_RESOURCE_ACCESS_KIND_DENY_CORS,
            )
            .map_err(format_windows_error)
    }
}

fn create_web_resource_response(
    environment: &ICoreWebView2Environment,
    asset_response: NativeAssetResponse,
) -> BridgeResult<ICoreWebView2WebResourceResponse> {
    let stream = unsafe { SHCreateMemStream(Some(&asset_response.bytes)) }
        .ok_or_else(|| "SHCreateMemStream returned null".to_string())?;
    unsafe {
        environment
            .CreateWebResourceResponse(
                &stream,
                asset_response.status_code,
                &HSTRING::from(asset_response.status_text),
                &HSTRING::from(asset_response.headers),
            )
            .map_err(format_windows_error)
    }
}

fn attach_ipc_handler(
    webview: &ICoreWebView2,
    native: NativeHandle,
) -> BridgeResult<EventRegistrationToken> {
    let mut token = EventRegistrationToken::default();
    unsafe {
        webview
            .add_WebMessageReceived(
                &WebMessageReceivedEventHandler::create(Box::new(move |_, args| {
                    let Some(args) = args else {
                        return Ok(());
                    };

                    let mut message = PWSTR::null();
                    args.TryGetWebMessageAsString(&mut message)?;
                    let message = take_pwstr(message);
                    if let Ok(view) = native.try_borrow() {
                        view.callbacks.on_message(message);
                    }
                    Ok(())
                })),
                &mut token,
            )
            .map_err(format_windows_error)?;
    }
    Ok(token)
}

fn attach_accelerator_key_handler(
    controller: &ICoreWebView2Controller,
    native: NativeHandle,
) -> BridgeResult<EventRegistrationToken> {
    let mut token = EventRegistrationToken::default();
    unsafe {
        controller
            .add_AcceleratorKeyPressed(
                &AcceleratorKeyPressedEventHandler::create(Box::new(move |_, args| {
                    let Some(args) = args else {
                        return Ok(());
                    };

                    let mut key_event_kind = COREWEBVIEW2_KEY_EVENT_KIND::default();
                    args.KeyEventKind(&mut key_event_kind)?;
                    let mut virtual_key = 0;
                    args.VirtualKey(&mut virtual_key)?;
                    let mut key_event_lparam = 0;
                    args.KeyEventLParam(&mut key_event_lparam)?;

                    let handled = native
                        .try_borrow()
                        .map(|view| {
                            view.callbacks.on_accelerator_key_pressed(
                                key_event_kind.0,
                                virtual_key as jint,
                                current_modifier_flags(),
                                key_event_lparam,
                            )
                        })
                        .unwrap_or(false);
                    if handled {
                        args.SetHandled(true)?;
                    }
                    Ok(())
                })),
                &mut token,
            )
            .map_err(format_windows_error)?;
    }
    Ok(token)
}

fn attach_got_focus_handler(
    controller: &ICoreWebView2Controller,
    native: NativeHandle,
) -> BridgeResult<EventRegistrationToken> {
    let mut token = EventRegistrationToken::default();
    unsafe {
        controller
            .add_GotFocus(
                &FocusChangedEventHandler::create(Box::new(move |_, _| {
                    if let Ok(view) = native.try_borrow() {
                        view.callbacks.on_focus_gained();
                    }
                    Ok(())
                })),
                &mut token,
            )
            .map_err(format_windows_error)?;
    }
    Ok(token)
}

fn fail_create(native: &NativeHandle, message: String) {
    let callbacks = match native.try_borrow() {
        Ok(view) => view.callbacks.clone(),
        Err(_) => return,
    };
    let handle = native_handle(native);
    if handle != 0 {
        unregister_shared_environment_view(handle);
    }
    if let Ok(mut view) = native.try_borrow_mut() {
        view.destroy();
    }
    callbacks.on_create_failed(message);
}

fn remove_execute_script_handler(native: &NativeHandle, handler_id: u64) {
    if let Ok(mut view) = native.try_borrow_mut() {
        view.execute_script_handlers
            .retain(|(id, _)| *id != handler_id);
    }
}

fn remove_dev_tools_handler(native: &NativeHandle, handler_id: u64) {
    if let Ok(mut view) = native.try_borrow_mut() {
        view.dev_tools_handlers.retain(|(id, _)| *id != handler_id);
    }
}

fn install_document_start_scripts(
    webview: &ICoreWebView2,
    native: NativeHandle,
) -> BridgeResult<()> {
    let scripts = native
        .try_borrow()
        .map_err(|_| "WebView2 state is busy while reading document start scripts".to_string())?
        .document_start_scripts
        .clone();
    for script in scripts {
        install_document_start_script(webview, native.clone(), script)?;
    }
    Ok(())
}

fn install_document_start_script(
    webview: &ICoreWebView2,
    native: NativeHandle,
    script: String,
) -> BridgeResult<()> {
    let (handler, handler_id) = {
        let mut view = native.try_borrow_mut().map_err(|_| {
            "WebView2 state is busy while installing document start script".to_string()
        })?;
        let handler_id = view.next_script_handler_id;
        view.next_script_handler_id += 1;
        let native_for_callback = native.clone();
        let handler =
            AddScriptToExecuteOnDocumentCreatedCompletedHandler::create(Box::new(move |_, _| {
                remove_add_script_handler(&native_for_callback, handler_id);
                Ok(())
            }));
        view.add_script_handlers.push((handler_id, handler.clone()));
        (handler, handler_id)
    };
    let result =
        unsafe { webview.AddScriptToExecuteOnDocumentCreated(&HSTRING::from(script), &handler) };
    if let Err(error) = result {
        remove_add_script_handler(&native, handler_id);
        return Err(format_windows_error(error));
    }
    Ok(())
}

fn remove_add_script_handler(native: &NativeHandle, handler_id: u64) {
    if let Ok(mut view) = native.try_borrow_mut() {
        view.add_script_handlers.retain(|(id, _)| *id != handler_id);
    }
}

fn create_container_hwnd(parent: HWND, callbacks: Rc<JavaCallbacks>) -> BridgeResult<HWND> {
    unsafe extern "system" fn window_proc(
        hwnd: HWND,
        msg: u32,
        wparam: WPARAM,
        lparam: LPARAM,
    ) -> LRESULT {
        if is_mouse_focus_message(msg, wparam) {
            unsafe {
                notify_before_mouse_focus(hwnd);
            }
        }
        if msg == WM_SETFOCUS {
            if let Ok(child) = GetWindow(hwnd, GW_CHILD) {
                let _ = SetFocus(Some(child));
            }
        }
        if msg == WM_USER_SHIFT_FALLBACK {
            unsafe {
                dispatch_pending_shift_event(hwnd, wparam.0);
            }
            return LRESULT(0);
        }
        let result = DefWindowProcW(hwnd, msg, wparam, lparam);
        if msg == WM_NCDESTROY {
            unsafe {
                unregister_keyboard_interop_window(hwnd);
                clear_container_callbacks(hwnd);
            }
        }
        result
    }

    let class_name = w!("IJ_WEBVIEW2_BRIDGE");
    let class = WNDCLASSEXW {
        cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
        style: CS_HREDRAW | CS_VREDRAW,
        lpfnWndProc: Some(window_proc),
        cbClsExtra: 0,
        cbWndExtra: 0,
        hInstance: unsafe { HINSTANCE(GetModuleHandleW(PCWSTR::null()).unwrap_or_default().0) },
        hIcon: HICON::default(),
        hCursor: HCURSOR::default(),
        hbrBackground: HBRUSH::default(),
        lpszMenuName: PCWSTR::null(),
        lpszClassName: class_name,
        hIconSm: HICON::default(),
    };
    unsafe {
        RegisterClassExW(&class);
    }

    let hwnd = unsafe {
        CreateWindowExW(
            WINDOW_EX_STYLE::default(),
            class_name,
            PCWSTR::null(),
            WS_CHILD | WS_CLIPCHILDREN | WS_CLIPSIBLINGS,
            0,
            0,
            0,
            0,
            Some(parent),
            None,
            GetModuleHandleW(PCWSTR::null()).map(Into::into).ok(),
            None,
        )
        .map_err(format_windows_error)?
    };

    unsafe {
        SetWindowLongPtrW(hwnd, GWLP_USERDATA, Rc::into_raw(callbacks) as isize);
        register_keyboard_interop_window(hwnd);
    }
    Ok(hwnd)
}

fn apply_bounds(view: &NativeWebView) -> BridgeResult<()> {
    apply_bounds_values(
        view.hwnd,
        view.controller.as_ref(),
        view.x,
        view.y,
        view.width,
        view.height,
        view.scale,
    )
}

fn apply_bounds_values(
    hwnd: HWND,
    controller: Option<&ICoreWebView2Controller>,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    scale: f64,
) -> BridgeResult<()> {
    if hwnd.0.is_null() {
        return Ok(());
    }

    let width = width.max(0);
    let height = height.max(0);
    let left = scale_to_i32(x, scale);
    let top = scale_to_i32(y, scale);
    let right = scale_to_i32(x.saturating_add(width), scale);
    let bottom = scale_to_i32(y.saturating_add(height), scale);
    let width = right.saturating_sub(left).max(0);
    let height = bottom.saturating_sub(top).max(0);

    unsafe {
        SetWindowPos(
            hwnd,
            None,
            left,
            top,
            width,
            height,
            SWP_ASYNCWINDOWPOS | SWP_NOACTIVATE | SWP_NOZORDER,
        )
        .map_err(format_windows_error)?;

        if let Some(controller) = controller {
            controller
                .SetBounds(RECT {
                    left: 0,
                    top: 0,
                    right: width,
                    bottom: height,
                })
                .map_err(format_windows_error)?;
            controller
                .NotifyParentWindowPositionChanged()
                .map_err(format_windows_error)?;
        }
    }
    Ok(())
}

fn run_with_handle<F>(env: &mut JNIEnv<'_>, handle: jlong, action: F)
where
    F: FnOnce(NativeHandle) -> BridgeResult<()>,
{
    if handle == 0 {
        let _ = env.throw_new(
            "java/lang/IllegalStateException",
            "WebView2 native handle is 0",
        );
        return;
    }

    let native = unsafe {
        let native = Rc::from_raw(handle as *const RefCell<NativeWebView>);
        let cloned = native.clone();
        let _ = Rc::into_raw(native);
        cloned
    };

    if let Err(message) = action(native) {
        let _ = env.throw_new("java/lang/IllegalStateException", message);
    }
}

fn current_modifier_flags() -> jint {
    let mut flags = 0;
    unsafe {
        if is_key_down(VK_SHIFT) {
            flags |= MODIFIER_SHIFT;
        }
        if is_key_down(VK_CONTROL) {
            flags |= MODIFIER_CONTROL;
        }
        if is_key_down(VK_MENU) {
            flags |= MODIFIER_ALT;
        }
        if is_key_down(VK_LWIN) || is_key_down(VK_RWIN) {
            flags |= MODIFIER_META;
        }
    }
    flags
}

unsafe fn is_key_down(virtual_key: VIRTUAL_KEY) -> bool {
    (GetKeyState(virtual_key.0 as i32) as u16 & 0x8000) != 0
}

fn modifier_flags_for_shift_event(key_event_kind: jint) -> jint {
    let mut flags = current_modifier_flags();
    if key_event_kind == COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN.0
        || key_event_kind == COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_DOWN.0
    {
        flags |= MODIFIER_SHIFT;
    } else {
        flags &= !MODIFIER_SHIFT;
    }
    flags
}

fn is_shift_key(virtual_key: jint) -> bool {
    virtual_key == VK_SHIFT.0 as jint
        || virtual_key == VK_LSHIFT.0 as jint
        || virtual_key == VK_RSHIFT.0 as jint
}

unsafe fn forward_system_key_to_awt_root_window(
    hwnd: HWND,
    message: u32,
    keyboard_event: &KBDLLHOOKSTRUCT,
) -> bool {
    // AWT/JBR can close windows and run menu/system-key handling only when the Java KeyEvent carries
    // the original native MSG. Recreating Alt+F4/F10/etc. in Kotlin would post a synthetic KeyEvent
    // without that MSG, so the hook forwards the real WM_SYSKEY* message to the root AWT window.
    let root = GetAncestor(hwnd, GA_ROOT);
    if !is_system_key_message(message) {
        return false;
    }
    !root.0.is_null()
        && PostMessageW(
            Some(root),
            message,
            WPARAM(keyboard_event.vkCode as usize),
            LPARAM(key_event_lparam_from_low_level_keyboard_event(keyboard_event) as isize),
        )
        .is_ok()
}

fn is_system_key_message(message: u32) -> bool {
    matches!(message, WM_SYSKEYDOWN | WM_SYSKEYUP)
}

unsafe fn register_keyboard_interop_window(hwnd: HWND) {
    KEYBOARD_INTEROP_WINDOWS.with(|windows| {
        let mut windows = windows.borrow_mut();
        if !windows.contains(&hwnd) {
            windows.push(hwnd);
        }
    });
    KEYBOARD_INTEROP_HOOK.with(|hook| {
        let mut hook = hook.borrow_mut();
        if hook.is_none() {
            if let Ok(installed_hook) =
                SetWindowsHookExW(WH_KEYBOARD_LL, Some(keyboard_interop_proc), None, 0)
            {
                *hook = Some(installed_hook);
            }
        }
    });
}

unsafe fn unregister_keyboard_interop_window(hwnd: HWND) {
    KEYBOARD_INTEROP_WINDOWS.with(|windows| {
        let mut windows = windows.borrow_mut();
        windows.retain(|registered_hwnd| *registered_hwnd != hwnd);
        if windows.is_empty() {
            KEYBOARD_INTEROP_HOOK.with(|hook| {
                if let Some(installed_hook) = hook.borrow_mut().take() {
                    let _ = UnhookWindowsHookEx(installed_hook);
                }
            });
        }
    });
    SHIFT_FALLBACK_EVENTS.with(|events| {
        events.borrow_mut().retain(|event| event.hwnd != hwnd);
    });
}

unsafe extern "system" fn keyboard_interop_proc(
    code: i32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    if code >= 0 {
        let keyboard_event = *(lparam.0 as *const KBDLLHOOKSTRUCT);
        handle_keyboard_interop_event(wparam.0 as u32, keyboard_event);
    }
    CallNextHookEx(None, code, wparam, lparam)
}

unsafe fn handle_keyboard_interop_event(message: u32, keyboard_event: KBDLLHOOKSTRUCT) {
    if !is_key_down_or_up_message(message) {
        return;
    }

    // System keys are owned by the Windows/AWT message pipeline. Kotlin deduplicates matching
    // WebView2 AcceleratorKeyPressed callbacks by key-event kind and does not synthesize them again.
    if is_system_key_message(message) {
        if let Some(hwnd) = focused_keyboard_interop_window() {
            forward_system_key_to_awt_root_window(hwnd, message, &keyboard_event);
        }
        return;
    }

    schedule_shift_fallback_if_needed(message, keyboard_event);
}

unsafe fn schedule_shift_fallback_if_needed(message: u32, keyboard_event: KBDLLHOOKSTRUCT) {
    let virtual_key = keyboard_event.vkCode as jint;
    if !is_shift_key(virtual_key) {
        return;
    }

    // WebView2 AcceleratorKeyPressed does not report bare Shift transitions, but the IDE gesture
    // handler needs them for double-Shift. Post them back to the container HWND so the JNI callback
    // runs on the WebView dispatcher thread and shares the normal Kotlin shortcut router.
    let Some(hwnd) = focused_keyboard_interop_window() else {
        return;
    };

    let key_event_kind = key_event_kind_from_message(message);
    let id = NEXT_SHIFT_FALLBACK_EVENT_ID.with(|next_id| {
        let id = next_id.get();
        next_id.set(id.wrapping_add(1).max(1));
        id
    });
    let event = PendingShiftEvent {
        id,
        hwnd,
        key_event_kind,
        virtual_key,
        modifiers: modifier_flags_for_shift_event(key_event_kind),
        key_event_lparam: key_event_lparam_from_low_level_keyboard_event(&keyboard_event),
    };
    SHIFT_FALLBACK_EVENTS.with(|events| {
        events.borrow_mut().push_back(event);
    });
    let _ = PostMessageW(Some(hwnd), WM_USER_SHIFT_FALLBACK, WPARAM(id), LPARAM(0));
}

unsafe fn focused_keyboard_interop_window() -> Option<HWND> {
    let foreground = GetForegroundWindow();
    if foreground.0.is_null() {
        return None;
    }
    let foreground_thread_id = GetWindowThreadProcessId(foreground, None);
    let mut gui_thread_info = GUITHREADINFO {
        cbSize: std::mem::size_of::<GUITHREADINFO>() as u32,
        ..Default::default()
    };
    if GetGUIThreadInfo(foreground_thread_id, &mut gui_thread_info).is_err() {
        return None;
    }

    let focus = gui_thread_info.hwndFocus;
    if focus.0.is_null() {
        return None;
    }
    KEYBOARD_INTEROP_WINDOWS.with(|windows| {
        windows
            .borrow()
            .iter()
            .copied()
            .find(|hwnd| *hwnd == focus || IsChild(*hwnd, focus).as_bool())
    })
}

fn is_key_down_or_up_message(message: u32) -> bool {
    matches!(message, WM_KEYDOWN | WM_KEYUP | WM_SYSKEYDOWN | WM_SYSKEYUP)
}

fn key_event_kind_from_message(message: u32) -> jint {
    match message {
        WM_SYSKEYDOWN => COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_DOWN.0,
        WM_SYSKEYUP => COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_UP.0,
        WM_KEYUP => COREWEBVIEW2_KEY_EVENT_KIND_KEY_UP.0,
        _ => COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN.0,
    }
}

fn key_event_lparam_from_low_level_keyboard_event(event: &KBDLLHOOKSTRUCT) -> jint {
    let mut lparam = 1 | ((event.scanCode as i32) << 16);
    if event.flags.contains(LLKHF_EXTENDED) {
        lparam |= 1 << 24;
    }
    if event.flags.contains(LLKHF_ALTDOWN) {
        lparam |= 1 << 29;
    }
    if event.flags.contains(LLKHF_UP) {
        lparam |= 1 << 30;
        lparam |= i32::MIN;
    }
    lparam
}

unsafe fn dispatch_pending_shift_event(hwnd: HWND, id: usize) {
    let event = SHIFT_FALLBACK_EVENTS.with(|events| {
        let mut events = events.borrow_mut();
        events
            .iter()
            .position(|event| event.id == id)
            .and_then(|index| events.remove(index))
    });
    let Some(event) = event else {
        return;
    };
    if event.hwnd != hwnd {
        return;
    }

    let callbacks_ptr = GetWindowLongPtrW(hwnd, GWLP_USERDATA) as *const JavaCallbacks;
    if callbacks_ptr.is_null() {
        return;
    }
    (*callbacks_ptr).on_accelerator_key_pressed(
        event.key_event_kind,
        event.virtual_key,
        event.modifiers,
        event.key_event_lparam,
    );
}

fn jstring_to_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> BridgeResult<String> {
    env.get_string(&value)
        .map(|value| value.into())
        .map_err(format_jni_error)
}

fn call_string_getter(
    env: &mut JNIEnv<'_>,
    object: &JObject<'_>,
    method_name: &str,
) -> BridgeResult<String> {
    let value = env
        .call_method(object, method_name, "()Ljava/lang/String;", &[])
        .map_err(format_jni_error)?
        .l()
        .map_err(format_jni_error)?;
    jstring_to_string(env, JString::from(value))
}

fn js_string_literal(value: &str) -> String {
    let mut result = String::with_capacity(value.len() + 2);
    result.push('\'');
    for ch in value.chars() {
        match ch {
            '\\' => result.push_str("\\\\"),
            '\'' => result.push_str("\\'"),
            '"' => result.push_str("\\\""),
            '\n' => result.push_str("\\n"),
            '\r' => result.push_str("\\r"),
            '\t' => result.push_str("\\t"),
            '\u{2028}' => result.push_str("\\u2028"),
            '\u{2029}' => result.push_str("\\u2029"),
            _ => result.push(ch),
        }
    }
    result.push('\'');
    result
}

fn scale_to_i32(value: i32, scale: f64) -> i32 {
    ((value as f64) * scale).round() as i32
}

unsafe fn get_pwstr<F>(read: F) -> Option<String>
where
    F: FnOnce(*mut PWSTR) -> windows::core::Result<()>,
{
    let mut value = PWSTR::null();
    read(&mut value).ok()?;
    if value.is_null() {
        return None;
    }
    Some(take_pwstr(value))
}

unsafe fn get_bool<F>(read: F) -> Option<bool>
where
    F: FnOnce(*mut windows::core::BOOL) -> windows::core::Result<()>,
{
    let mut value = windows::core::BOOL(0);
    read(&mut value).ok()?;
    Some(value.as_bool())
}

unsafe fn get_i32<F>(read: F) -> Option<i32>
where
    F: FnOnce(*mut i32) -> windows::core::Result<()>,
{
    let mut value = 0;
    read(&mut value).ok()?;
    Some(value)
}

unsafe fn get_u32<F>(read: F) -> Option<u32>
where
    F: FnOnce(*mut u32) -> windows::core::Result<()>,
{
    let mut value = 0;
    read(&mut value).ok()?;
    Some(value)
}

unsafe fn get_u64<F>(read: F) -> Option<u64>
where
    F: FnOnce(*mut u64) -> windows::core::Result<()>,
{
    let mut value = 0;
    read(&mut value).ok()?;
    Some(value)
}

unsafe fn get_web_error_status<F>(read: F) -> Option<COREWEBVIEW2_WEB_ERROR_STATUS>
where
    F: FnOnce(*mut COREWEBVIEW2_WEB_ERROR_STATUS) -> windows::core::Result<()>,
{
    let mut value = COREWEBVIEW2_WEB_ERROR_STATUS::default();
    read(&mut value).ok()?;
    Some(value)
}

unsafe fn get_process_failed_kind<F>(read: F) -> Option<COREWEBVIEW2_PROCESS_FAILED_KIND>
where
    F: FnOnce(*mut COREWEBVIEW2_PROCESS_FAILED_KIND) -> windows::core::Result<()>,
{
    let mut value = COREWEBVIEW2_PROCESS_FAILED_KIND::default();
    read(&mut value).ok()?;
    Some(value)
}

unsafe fn get_process_failed_reason<F>(read: F) -> Option<COREWEBVIEW2_PROCESS_FAILED_REASON>
where
    F: FnOnce(*mut COREWEBVIEW2_PROCESS_FAILED_REASON) -> windows::core::Result<()>,
{
    let mut value = COREWEBVIEW2_PROCESS_FAILED_REASON::default();
    read(&mut value).ok()?;
    Some(value)
}

unsafe fn get_browser_exit_kind<F>(read: F) -> Option<COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND>
where
    F: FnOnce(*mut COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND) -> windows::core::Result<()>,
{
    let mut value = COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND::default();
    read(&mut value).ok()?;
    Some(value)
}

unsafe fn get_process_kind<F>(read: F) -> Option<COREWEBVIEW2_PROCESS_KIND>
where
    F: FnOnce(*mut COREWEBVIEW2_PROCESS_KIND) -> windows::core::Result<()>,
{
    let mut value = COREWEBVIEW2_PROCESS_KIND::default();
    read(&mut value).ok()?;
    Some(value)
}

unsafe fn get_permission_kind<F>(read: F) -> Option<COREWEBVIEW2_PERMISSION_KIND>
where
    F: FnOnce(*mut COREWEBVIEW2_PERMISSION_KIND) -> windows::core::Result<()>,
{
    let mut value = COREWEBVIEW2_PERMISSION_KIND::default();
    read(&mut value).ok()?;
    Some(value)
}

fn is_mouse_focus_message(msg: u32, wparam: WPARAM) -> bool {
    match msg {
        WM_MOUSEACTIVATE | WM_LBUTTONDOWN | WM_RBUTTONDOWN | WM_MBUTTONDOWN | WM_XBUTTONDOWN => {
            true
        }
        WM_PARENTNOTIFY => matches!(
            (wparam.0 & 0xffff) as u32,
            WM_LBUTTONDOWN | WM_RBUTTONDOWN | WM_MBUTTONDOWN | WM_XBUTTONDOWN
        ),
        _ => false,
    }
}

unsafe fn notify_before_mouse_focus(hwnd: HWND) {
    let callbacks_ptr = GetWindowLongPtrW(hwnd, GWLP_USERDATA) as *const JavaCallbacks;
    if callbacks_ptr.is_null() {
        return;
    }
    (*callbacks_ptr).on_before_mouse_focus();
}

unsafe fn clear_container_callbacks(hwnd: HWND) {
    let callbacks_ptr = GetWindowLongPtrW(hwnd, GWLP_USERDATA) as *const JavaCallbacks;
    if callbacks_ptr.is_null() {
        return;
    }
    SetWindowLongPtrW(hwnd, GWLP_USERDATA, 0);
    drop(Rc::from_raw(callbacks_ptr));
}

fn diagnostic_data(pairs: Vec<(&str, String)>) -> String {
    pairs
        .into_iter()
        .filter(|(_, value)| !value.is_empty())
        .map(|(name, value)| format!("{}={}", name, sanitize_diagnostic_value(&value)))
        .collect::<Vec<_>>()
        .join("\n")
}

fn sanitize_diagnostic_value(value: &str) -> String {
    value.replace('\r', " ").replace('\n', " ")
}

fn process_failed_kind_name(kind: COREWEBVIEW2_PROCESS_FAILED_KIND) -> &'static str {
    match kind {
        COREWEBVIEW2_PROCESS_FAILED_KIND_BROWSER_PROCESS_EXITED => "browser-process-exited",
        COREWEBVIEW2_PROCESS_FAILED_KIND_RENDER_PROCESS_EXITED => "render-process-exited",
        COREWEBVIEW2_PROCESS_FAILED_KIND_RENDER_PROCESS_UNRESPONSIVE => {
            "render-process-unresponsive"
        }
        COREWEBVIEW2_PROCESS_FAILED_KIND_FRAME_RENDER_PROCESS_EXITED => {
            "frame-render-process-exited"
        }
        COREWEBVIEW2_PROCESS_FAILED_KIND_UTILITY_PROCESS_EXITED => "utility-process-exited",
        COREWEBVIEW2_PROCESS_FAILED_KIND_SANDBOX_HELPER_PROCESS_EXITED => {
            "sandbox-helper-process-exited"
        }
        COREWEBVIEW2_PROCESS_FAILED_KIND_GPU_PROCESS_EXITED => "gpu-process-exited",
        COREWEBVIEW2_PROCESS_FAILED_KIND_PPAPI_PLUGIN_PROCESS_EXITED => {
            "ppapi-plugin-process-exited"
        }
        COREWEBVIEW2_PROCESS_FAILED_KIND_PPAPI_BROKER_PROCESS_EXITED => {
            "ppapi-broker-process-exited"
        }
        COREWEBVIEW2_PROCESS_FAILED_KIND_UNKNOWN_PROCESS_EXITED => "unknown-process-exited",
        _ => "unknown",
    }
}

fn process_failed_reason_name(reason: COREWEBVIEW2_PROCESS_FAILED_REASON) -> &'static str {
    match reason {
        COREWEBVIEW2_PROCESS_FAILED_REASON_UNEXPECTED => "unexpected",
        COREWEBVIEW2_PROCESS_FAILED_REASON_UNRESPONSIVE => "unresponsive",
        COREWEBVIEW2_PROCESS_FAILED_REASON_TERMINATED => "terminated",
        COREWEBVIEW2_PROCESS_FAILED_REASON_CRASHED => "crashed",
        COREWEBVIEW2_PROCESS_FAILED_REASON_LAUNCH_FAILED => "launch-failed",
        COREWEBVIEW2_PROCESS_FAILED_REASON_OUT_OF_MEMORY => "out-of-memory",
        COREWEBVIEW2_PROCESS_FAILED_REASON_PROFILE_DELETED => "profile-deleted",
        _ => "unknown",
    }
}

fn browser_exit_kind_name(kind: COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND) -> &'static str {
    match kind {
        COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND_NORMAL => "normal",
        COREWEBVIEW2_BROWSER_PROCESS_EXIT_KIND_FAILED => "failed",
        _ => "unknown",
    }
}

fn process_kind_name(kind: COREWEBVIEW2_PROCESS_KIND) -> &'static str {
    match kind {
        COREWEBVIEW2_PROCESS_KIND_BROWSER => "browser",
        COREWEBVIEW2_PROCESS_KIND_RENDERER => "renderer",
        COREWEBVIEW2_PROCESS_KIND_UTILITY => "utility",
        COREWEBVIEW2_PROCESS_KIND_SANDBOX_HELPER => "sandbox-helper",
        COREWEBVIEW2_PROCESS_KIND_GPU => "gpu",
        COREWEBVIEW2_PROCESS_KIND_PPAPI_PLUGIN => "ppapi-plugin",
        COREWEBVIEW2_PROCESS_KIND_PPAPI_BROKER => "ppapi-broker",
        _ => "unknown",
    }
}

fn permission_kind_name(kind: COREWEBVIEW2_PERMISSION_KIND) -> &'static str {
    match kind {
        COREWEBVIEW2_PERMISSION_KIND_UNKNOWN_PERMISSION => "unknown",
        COREWEBVIEW2_PERMISSION_KIND_MICROPHONE => "microphone",
        COREWEBVIEW2_PERMISSION_KIND_CAMERA => "camera",
        COREWEBVIEW2_PERMISSION_KIND_GEOLOCATION => "geolocation",
        COREWEBVIEW2_PERMISSION_KIND_NOTIFICATIONS => "notifications",
        COREWEBVIEW2_PERMISSION_KIND_OTHER_SENSORS => "other-sensors",
        COREWEBVIEW2_PERMISSION_KIND_CLIPBOARD_READ => "clipboard-read",
        COREWEBVIEW2_PERMISSION_KIND_MULTIPLE_AUTOMATIC_DOWNLOADS => "multiple-automatic-downloads",
        COREWEBVIEW2_PERMISSION_KIND_FILE_READ_WRITE => "file-read-write",
        COREWEBVIEW2_PERMISSION_KIND_AUTOPLAY => "autoplay",
        COREWEBVIEW2_PERMISSION_KIND_LOCAL_FONTS => "local-fonts",
        COREWEBVIEW2_PERMISSION_KIND_MIDI_SYSTEM_EXCLUSIVE_MESSAGES => {
            "midi-system-exclusive-messages"
        }
        COREWEBVIEW2_PERMISSION_KIND_WINDOW_MANAGEMENT => "window-management",
        _ => "unknown",
    }
}

fn format_windows_error<E: std::fmt::Debug>(error: E) -> String {
    format!("{error:?}")
}

fn format_jni_error(error: jni::errors::Error) -> String {
    format!("{error:?}")
}
