// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#![cfg(target_os = "linux")]
#![allow(non_camel_case_types)]

use std::{
    env,
    ffi::{c_char, c_int, c_uint, c_ulong, c_void, CStr, CString},
    ptr, slice,
    sync::{mpsc, Arc, Mutex},
    thread,
    time::Duration,
};

use jni::{
    objects::{GlobalRef, JClass, JObject, JString, JValue},
    sys::{jboolean, jdouble, jint, jlong, jstring},
    JNIEnv, JavaVM,
};

type BridgeResult<T> = std::result::Result<T, String>;
type GtkNative = Arc<Mutex<NativeWebView>>;
type gboolean = c_int;
type gint = c_int;
type guint = c_uint;
type gulong = c_ulong;
type gssize = isize;
type gpointer = *mut c_void;

const G_PRIORITY_DEFAULT: gint = 0;
const G_SOURCE_REMOVE: gboolean = 0;
const GTK_WINDOW_POPUP: gint = 1;
const IPC_HANDLER_NAME: &str = "webviewIpc";
const REVERT_TO_PARENT: c_int = 2;
const CURRENT_TIME: c_ulong = 0;
const WEBKIT_SNAPSHOT_REGION_VISIBLE: gint = 0;
const WEBKIT_SNAPSHOT_OPTIONS_NONE: guint = 0;
const WEBKIT_HARDWARE_ACCELERATION_POLICY_NEVER: gint = 2;
const NATIVE_ABI_VERSION: &str = "wvi-linux-webkitgtk-v1";

#[derive(Clone, Copy, PartialEq, Eq)]
enum Backend {
    X11,
    WaylandSnapshot,
}

impl Backend {
    fn from_native_id(id: jint) -> BridgeResult<Self> {
        match id {
            0 => Ok(Self::X11),
            1 => Ok(Self::WaylandSnapshot),
            _ => Err(format!("unknown Linux WebKitGTK backend id: {id}")),
        }
    }

    fn gdk_backend_name(self) -> &'static str {
        match self {
            Self::X11 => "x11",
            Self::WaylandSnapshot => "wayland",
        }
    }
}

#[repr(C)]
struct GtkWidget {
    _private: [u8; 0],
}

#[repr(C)]
struct GtkContainer {
    _private: [u8; 0],
}

#[repr(C)]
struct WebKitWebView {
    _private: [u8; 0],
}

#[repr(C)]
struct WebKitUserContentManager {
    _private: [u8; 0],
}

#[repr(C)]
struct WebKitWebContext {
    _private: [u8; 0],
}

#[repr(C)]
struct WebKitSettings {
    _private: [u8; 0],
}

#[repr(C)]
struct WebKitJavascriptResult {
    _private: [u8; 0],
}

#[repr(C)]
struct JSCValue {
    _private: [u8; 0],
}

#[repr(C)]
struct GObject {
    _private: [u8; 0],
}

#[repr(C)]
struct GAsyncResult {
    _private: [u8; 0],
}

#[repr(C)]
struct GCancellable {
    _private: [u8; 0],
}

#[repr(C)]
struct GMainContext {
    _private: [u8; 0],
}

#[repr(C)]
struct GClosure {
    _private: [u8; 0],
}

#[repr(C)]
struct GdkWindow {
    _private: [u8; 0],
}

#[repr(C)]
struct GdkDisplay {
    _private: [u8; 0],
}

#[repr(C)]
struct CairoSurface {
    _private: [u8; 0],
}

#[repr(C)]
struct GError {
    domain: guint,
    code: gint,
    message: *mut c_char,
}

type GSourceFunc = Option<unsafe extern "C" fn(gpointer) -> gboolean>;
type GDestroyNotify = Option<unsafe extern "C" fn(gpointer)>;
type GClosureNotify = Option<unsafe extern "C" fn(gpointer, *mut GClosure)>;
type GAsyncReadyCallback = Option<unsafe extern "C" fn(*mut GObject, *mut GAsyncResult, gpointer)>;

extern "C" {
    fn gdk_set_allowed_backends(backends: *const c_char);

    fn gtk_init_check(argc: *mut c_int, argv: *mut *mut *mut c_char) -> gboolean;
    fn gtk_main();
    fn gtk_main_quit();
    fn gtk_offscreen_window_new() -> *mut GtkWidget;
    fn gtk_window_new(window_type: gint) -> *mut GtkWidget;
    fn gtk_window_set_decorated(window: *mut GtkWidget, setting: gboolean);
    fn gtk_window_set_resizable(window: *mut GtkWidget, resizable: gboolean);
    fn gtk_window_set_skip_pager_hint(window: *mut GtkWidget, setting: gboolean);
    fn gtk_window_set_skip_taskbar_hint(window: *mut GtkWidget, setting: gboolean);
    fn gtk_window_resize(window: *mut GtkWidget, width: gint, height: gint);
    fn gtk_container_add(container: *mut GtkContainer, widget: *mut GtkWidget);
    fn gtk_widget_destroy(widget: *mut GtkWidget);
    fn gtk_widget_get_window(widget: *mut GtkWidget) -> *mut GdkWindow;
    fn gtk_widget_grab_focus(widget: *mut GtkWidget) -> gboolean;
    fn gtk_widget_hide(widget: *mut GtkWidget);
    fn gtk_widget_realize(widget: *mut GtkWidget);
    fn gtk_widget_set_can_focus(widget: *mut GtkWidget, can_focus: gboolean);
    fn gtk_widget_set_size_request(widget: *mut GtkWidget, width: gint, height: gint);
    fn gtk_widget_show_all(widget: *mut GtkWidget);

    fn webkit_user_content_manager_new() -> *mut WebKitUserContentManager;
    fn webkit_user_content_manager_register_script_message_handler(
        manager: *mut WebKitUserContentManager,
        name: *const c_char,
    ) -> gboolean;
    fn webkit_web_view_new_with_user_content_manager(
        manager: *mut WebKitUserContentManager,
    ) -> *mut GtkWidget;
    fn webkit_web_view_get_context(web_view: *mut WebKitWebView) -> *mut WebKitWebContext;
    fn webkit_web_view_get_settings(web_view: *mut WebKitWebView) -> *mut WebKitSettings;
    fn webkit_settings_set_hardware_acceleration_policy(
        settings: *mut WebKitSettings,
        policy: gint,
    );
    fn webkit_web_view_load_html(
        web_view: *mut WebKitWebView,
        content: *const c_char,
        base_uri: *const c_char,
    );
    fn webkit_web_view_load_uri(web_view: *mut WebKitWebView, uri: *const c_char);
    fn webkit_web_view_evaluate_javascript(
        web_view: *mut WebKitWebView,
        script: *const c_char,
        length: gssize,
        world_name: *const c_char,
        source_uri: *const c_char,
        cancellable: *mut GCancellable,
        callback: GAsyncReadyCallback,
        user_data: gpointer,
    );
    fn webkit_web_view_evaluate_javascript_finish(
        web_view: *mut WebKitWebView,
        result: *mut GAsyncResult,
        error: *mut *mut GError,
    ) -> *mut JSCValue;
    fn webkit_web_view_get_snapshot(
        web_view: *mut WebKitWebView,
        region: gint,
        options: guint,
        cancellable: *mut GCancellable,
        callback: GAsyncReadyCallback,
        user_data: gpointer,
    );
    fn webkit_web_view_get_snapshot_finish(
        web_view: *mut WebKitWebView,
        result: *mut GAsyncResult,
        error: *mut *mut GError,
    ) -> *mut CairoSurface;
    fn webkit_javascript_result_get_js_value(result: *mut WebKitJavascriptResult) -> *mut JSCValue;

    fn jsc_value_to_string(value: *mut JSCValue) -> *mut c_char;

    fn cairo_surface_destroy(surface: *mut CairoSurface);
    fn cairo_surface_flush(surface: *mut CairoSurface);
    fn cairo_surface_map_to_image(
        surface: *mut CairoSurface,
        extents: *const c_void,
    ) -> *mut CairoSurface;
    fn cairo_surface_unmap_image(surface: *mut CairoSurface, image: *mut CairoSurface);
    fn cairo_image_surface_get_width(surface: *mut CairoSurface) -> c_int;
    fn cairo_image_surface_get_height(surface: *mut CairoSurface) -> c_int;
    fn cairo_image_surface_get_stride(surface: *mut CairoSurface) -> c_int;
    fn cairo_image_surface_get_data(surface: *mut CairoSurface) -> *mut u8;

    fn g_error_free(error: *mut GError);
    fn g_free(memory: gpointer);
    fn g_main_context_invoke_full(
        context: *mut GMainContext,
        priority: gint,
        function: GSourceFunc,
        data: gpointer,
        notify: GDestroyNotify,
    );
    fn g_object_unref(object: gpointer);
    fn g_object_ref(object: gpointer) -> gpointer;
    fn g_timeout_add(interval: guint, function: GSourceFunc, data: gpointer) -> guint;
    fn g_signal_connect_data(
        instance: gpointer,
        detailed_signal: *const c_char,
        c_handler: Option<unsafe extern "C" fn()>,
        data: gpointer,
        destroy_data: GClosureNotify,
        connect_flags: gint,
    ) -> gulong;

    fn gdk_window_get_display(window: *mut GdkWindow) -> *mut GdkDisplay;
    fn gdk_x11_display_get_xdisplay(display: *mut GdkDisplay) -> gpointer;
    fn gdk_x11_window_get_xid(window: *mut GdkWindow) -> c_ulong;

    fn XFlush(display: gpointer) -> c_int;
    fn XMapRaised(display: gpointer, window: c_ulong) -> c_int;
    fn XMoveResizeWindow(
        display: gpointer,
        window: c_ulong,
        x: c_int,
        y: c_int,
        width: c_uint,
        height: c_uint,
    ) -> c_int;
    fn XReparentWindow(
        display: gpointer,
        window: c_ulong,
        parent: c_ulong,
        x: c_int,
        y: c_int,
    ) -> c_int;
    fn XSetInputFocus(display: gpointer, focus: c_ulong, revert_to: c_int, time: c_ulong) -> c_int;
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

    fn on_snapshot(&self, width: jint, height: jint, pixels: Vec<jint>) {
        self.with_env(|env, object| {
            let array = env.new_int_array(pixels.len() as jint)?;
            env.set_int_array_region(&array, 0, &pixels)?;
            let array = JObject::from(array);
            env.call_method(
                object,
                "onSnapshot",
                "(II[I)V",
                &[
                    JValue::Int(width),
                    JValue::Int(height),
                    JValue::Object(&array),
                ],
            )?;
            Ok(())
        });
    }

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

struct NativeHandle(GtkNative);

struct NativeWebView {
    handle: jlong,
    callbacks: JavaCallbacks,
    backend: Backend,
    parent_xid: c_ulong,
    window_xid: c_ulong,
    display: gpointer,
    window: *mut GtkWidget,
    webview: *mut GtkWidget,
    destroyed: bool,
    created: bool,
    visible: bool,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    scale: f64,
    snapshot_in_flight: bool,
    snapshot_pending: bool,
    snapshot_count: u64,
    snapshot_skip_count: u64,
    destroy_pending: bool,
}

unsafe impl Send for NativeWebView {}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_abiVersionNative(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    env.new_string(NATIVE_ABI_VERSION)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_createNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    parent_xid: jlong,
    backend: jint,
    callbacks: JObject<'_>,
) -> jlong {
    match create_native(&mut env, parent_xid, backend, callbacks) {
        Ok(handle) => handle,
        Err(message) => {
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_destroyNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    let Some(native) = take_native_handle(&mut env, handle) else {
        return;
    };
    let _ = enqueue_gtk_task_and_wait(move || destroy_native(native));
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_attachToParentNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    parent_xid: jlong,
) {
    run_with_handle(&mut env, handle, move |native| {
        let parent_xid = parent_xid as c_ulong;
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                view.parent_xid = parent_xid;
                apply_parent_and_bounds(view);
            });
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_detachNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                if !view.window.is_null() {
                    unsafe {
                        gtk_widget_hide(view.window);
                    }
                }
                view.parent_xid = 0;
            });
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_setBoundsNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    x: jint,
    y: jint,
    width: jint,
    height: jint,
    scale: jdouble,
) {
    run_with_handle(&mut env, handle, move |native| {
        let snapshot_native = native.clone();
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                view.x = x;
                view.y = y;
                view.width = width.max(0);
                view.height = height.max(0);
                view.scale = if scale > 0.0 { scale } else { 1.0 };
                apply_bounds(view);
            });
            request_snapshot_later(snapshot_native, 50);
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_setVisibleNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    visible: jboolean,
) {
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                view.visible = visible != 0;
                apply_visibility(view);
            });
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_focusNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                if !view.webview.is_null() {
                    unsafe {
                        gtk_widget_grab_focus(view.webview);
                    }
                }
                if view.backend == Backend::WaylandSnapshot {
                    return;
                }
                if !view.display.is_null() && view.window_xid != 0 {
                    unsafe {
                        XSetInputFocus(
                            view.display,
                            view.window_xid,
                            REVERT_TO_PARENT,
                            CURRENT_TIME,
                        );
                        XFlush(view.display);
                    }
                }
            });
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_clearFocusNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                if view.backend == Backend::WaylandSnapshot {
                    return;
                }
                if !view.display.is_null() && view.parent_xid != 0 {
                    unsafe {
                        XSetInputFocus(
                            view.display,
                            view.parent_xid,
                            REVERT_TO_PARENT,
                            CURRENT_TIME,
                        );
                        XFlush(view.display);
                    }
                }
            });
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_loadUrlNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    url: JString<'_>,
) {
    let Ok(url) = jstring_to_string(&mut env, url) else {
        return;
    };
    run_with_handle(&mut env, handle, move |native| {
        let snapshot_native = native.clone();
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                let Ok(url) = CString::new(url) else {
                    view.callbacks.on_log(
                        2,
                        "LinuxWebKitGtkBridge: URL contains a NUL byte".to_string(),
                    );
                    return;
                };
                if !view.webview.is_null() {
                    unsafe {
                        webkit_web_view_load_uri(view.webview as *mut WebKitWebView, url.as_ptr());
                    }
                }
            });
            request_snapshot_later(snapshot_native, 250);
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_loadHtmlNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    html: JString<'_>,
    base_url: JObject<'_>,
) {
    let Ok(html) = jstring_to_string(&mut env, html) else {
        return;
    };
    let base_url = if base_url.is_null() {
        None
    } else {
        let base_url = JString::from(base_url);
        match jstring_to_string(&mut env, base_url) {
            Ok(value) => Some(value),
            Err(_) => return,
        }
    };

    run_with_handle(&mut env, handle, move |native| {
        let snapshot_native = native.clone();
        enqueue_gtk_task(move || {
            with_locked_view(&native, |view| {
                let Ok(html) = CString::new(html) else {
                    view.callbacks.on_log(
                        2,
                        "LinuxWebKitGtkBridge: HTML contains a NUL byte".to_string(),
                    );
                    return;
                };
                let base_url = match base_url {
                    Some(value) => match CString::new(value) {
                        Ok(value) => Some(value),
                        Err(_) => {
                            view.callbacks.on_log(
                                2,
                                "LinuxWebKitGtkBridge: base URL contains a NUL byte".to_string(),
                            );
                            return;
                        }
                    },
                    None => None,
                };
                let base_url_ptr = base_url
                    .as_ref()
                    .map(|value| value.as_ptr())
                    .unwrap_or(ptr::null());
                if !view.webview.is_null() {
                    unsafe {
                        webkit_web_view_load_html(
                            view.webview as *mut WebKitWebView,
                            html.as_ptr(),
                            base_url_ptr,
                        );
                    }
                }
            });
            request_snapshot_later(snapshot_native, 250);
        })
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_evaluateJavaScriptNative(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    eval_id: jlong,
    script: JString<'_>,
) {
    let Ok(script) = jstring_to_string(&mut env, script) else {
        return;
    };
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || evaluate_javascript(native, eval_id, script))
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_transferToJsNative(
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
    run_with_handle(&mut env, handle, move |native| {
        enqueue_gtk_task(move || evaluate_javascript_ignoring_result(native, script))
    });
}

#[no_mangle]
pub extern "system" fn Java_com_intellij_ui_webview_internal_linux_LinuxWebKitGtkBridge_shutdownRuntimeNative(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    if GTK_RUNTIME.get().is_none() {
        return;
    }
    let _ = enqueue_gtk_task_and_wait(|| unsafe {
        gtk_main_quit();
    });
}

fn create_native(
    env: &mut JNIEnv<'_>,
    parent_xid: jlong,
    backend: jint,
    callbacks: JObject<'_>,
) -> BridgeResult<jlong> {
    let backend = Backend::from_native_id(backend)?;
    ensure_gtk_runtime(backend)?;

    let callbacks = JavaCallbacks {
        vm: env.get_java_vm().map_err(format_jni_error)?,
        object: env.new_global_ref(callbacks).map_err(format_jni_error)?,
    };
    let native = Arc::new(Mutex::new(NativeWebView {
        handle: 0,
        callbacks,
        backend,
        parent_xid: parent_xid as c_ulong,
        window_xid: 0,
        display: ptr::null_mut(),
        window: ptr::null_mut(),
        webview: ptr::null_mut(),
        destroyed: false,
        created: false,
        visible: true,
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        scale: 1.0,
        snapshot_in_flight: false,
        snapshot_pending: false,
        snapshot_count: 0,
        snapshot_skip_count: 0,
        destroy_pending: false,
    }));

    let handle_box = Box::new(NativeHandle(native.clone()));
    let handle = Box::into_raw(handle_box) as jlong;
    if let Ok(mut view) = native.lock() {
        view.handle = handle;
    }

    enqueue_gtk_task(move || create_widgets(native))?;
    Ok(handle)
}

fn create_widgets(native: GtkNative) {
    let mut view = match native.lock() {
        Ok(view) => view,
        Err(_) => return,
    };
    if view.destroyed || view.created {
        return;
    }

    unsafe {
        let window = match view.backend {
            Backend::X11 => gtk_window_new(GTK_WINDOW_POPUP),
            Backend::WaylandSnapshot => gtk_offscreen_window_new(),
        };
        if window.is_null() {
            drop(view);
            fail_create(&native, "gtk_window_new returned null".to_string());
            return;
        }
        if view.backend == Backend::X11 {
            gtk_window_set_decorated(window, 0);
            gtk_window_set_resizable(window, 1);
            gtk_window_set_skip_pager_hint(window, 1);
            gtk_window_set_skip_taskbar_hint(window, 1);
        }

        let manager = webkit_user_content_manager_new();
        if manager.is_null() {
            gtk_widget_destroy(window);
            drop(view);
            fail_create(
                &native,
                "webkit_user_content_manager_new returned null".to_string(),
            );
            return;
        }

        let detailed_signal = CString::new(format!("script-message-received::{IPC_HANDLER_NAME}"))
            .expect("static signal name");
        let signal_native = Arc::into_raw(native.clone()) as gpointer;
        g_signal_connect_data(
            manager as gpointer,
            detailed_signal.as_ptr(),
            Some(std::mem::transmute::<
                unsafe extern "C" fn(
                    *mut WebKitUserContentManager,
                    *mut WebKitJavascriptResult,
                    gpointer,
                ),
                unsafe extern "C" fn(),
            >(script_message_received)),
            signal_native,
            Some(drop_native_arc),
            0,
        );

        let handler_name = CString::new(IPC_HANDLER_NAME).expect("static IPC handler name");
        if webkit_user_content_manager_register_script_message_handler(
            manager,
            handler_name.as_ptr(),
        ) == 0
        {
            g_object_unref(manager as gpointer);
            gtk_widget_destroy(window);
            drop(view);
            fail_create(
                &native,
                "failed to register WebKitGTK script message handler".to_string(),
            );
            return;
        }

        let webview = webkit_web_view_new_with_user_content_manager(manager);
        g_object_unref(manager as gpointer);
        if webview.is_null() {
            gtk_widget_destroy(window);
            drop(view);
            fail_create(
                &native,
                "webkit_web_view_new_with_user_content_manager returned null".to_string(),
            );
            return;
        }
        if view.backend == Backend::WaylandSnapshot {
            disable_hardware_acceleration(webview);
        }
        pin_web_context(webview);

        gtk_widget_set_can_focus(webview, 1);
        gtk_container_add(window as *mut GtkContainer, webview);
        let load_changed_native = Arc::into_raw(native.clone()) as gpointer;
        let load_changed_signal = CString::new("load-changed").expect("static signal name");
        g_signal_connect_data(
            webview as gpointer,
            load_changed_signal.as_ptr(),
            Some(std::mem::transmute::<
                unsafe extern "C" fn(*mut WebKitWebView, gint, gpointer),
                unsafe extern "C" fn(),
            >(load_changed)),
            load_changed_native,
            Some(drop_native_arc),
            0,
        );

        gtk_widget_set_size_request(window, 1, 1);
        gtk_widget_realize(window);

        let mut display = ptr::null_mut();
        let mut window_xid = 0;
        if view.backend == Backend::X11 {
            let gdk_window = gtk_widget_get_window(window);
            if gdk_window.is_null() {
                gtk_widget_destroy(window);
                drop(view);
                fail_create(&native, "gtk_widget_get_window returned null".to_string());
                return;
            }

            let gdk_display = gdk_window_get_display(gdk_window);
            display = if gdk_display.is_null() {
                ptr::null_mut()
            } else {
                gdk_x11_display_get_xdisplay(gdk_display)
            };
            window_xid = gdk_x11_window_get_xid(gdk_window);
            if display.is_null() || window_xid == 0 {
                gtk_widget_destroy(window);
                drop(view);
                fail_create(&native, "failed to resolve GTK X11 window id".to_string());
                return;
            }
        }

        view.window = window;
        view.webview = webview;
        view.display = display;
        view.window_xid = window_xid;
        view.created = true;
        apply_parent_and_bounds(&mut view);
        apply_visibility(&view);
        view.callbacks.on_created(view.handle);
        if view.backend == Backend::WaylandSnapshot {
            view.callbacks.on_log(
                0,
                "LinuxWebKitGtkBridge: Wayland WebKitGTK widgets created".to_string(),
            );
            request_snapshot_later(native.clone(), 100);
        }
    }
}

fn fail_create(native: &GtkNative, message: String) {
    if let Ok(mut view) = native.lock() {
        view.destroyed = true;
        destroy_widgets(&mut view);
        view.callbacks.on_create_failed(message);
    }
}

fn pin_web_context(webview: *mut GtkWidget) {
    static PIN_DEFAULT_CONTEXT: std::sync::Once = std::sync::Once::new();
    PIN_DEFAULT_CONTEXT.call_once(|| unsafe {
        let context = webkit_web_view_get_context(webview as *mut WebKitWebView);
        if !context.is_null() {
            // WebKitGTK may otherwise finalize the default context from the JVM shutdown thread.
            let _ = g_object_ref(context as gpointer);
        }
    });
}

unsafe fn disable_hardware_acceleration(webview: *mut GtkWidget) {
    let settings = webkit_web_view_get_settings(webview as *mut WebKitWebView);
    if !settings.is_null() {
        webkit_settings_set_hardware_acceleration_policy(
            settings,
            WEBKIT_HARDWARE_ACCELERATION_POLICY_NEVER,
        );
    }
}

fn destroy_native(native: GtkNative) {
    if let Ok(mut view) = native.lock() {
        view.destroyed = true;
        if view.backend == Backend::WaylandSnapshot && view.snapshot_in_flight {
            view.destroy_pending = true;
            if !view.window.is_null() {
                unsafe {
                    gtk_widget_hide(view.window);
                }
            }
            return;
        }
        destroy_widgets(&mut view);
    }
}

fn destroy_widgets(view: &mut NativeWebView) {
    if !view.window.is_null() {
        unsafe {
            gtk_widget_destroy(view.window);
        }
    }
    view.window = ptr::null_mut();
    view.webview = ptr::null_mut();
    view.display = ptr::null_mut();
    view.window_xid = 0;
    view.created = false;
    view.snapshot_in_flight = false;
    view.snapshot_pending = false;
    view.destroy_pending = false;
}

fn apply_parent_and_bounds(view: &mut NativeWebView) {
    if view.destroyed || !view.created {
        return;
    }
    if view.backend == Backend::WaylandSnapshot {
        apply_bounds(view);
        return;
    }
    if view.parent_xid == 0 || view.display.is_null() || view.window_xid == 0 {
        return;
    }
    let (x, y, width, height) = scaled_bounds(view);
    unsafe {
        XReparentWindow(view.display, view.window_xid, view.parent_xid, x, y);
        XMoveResizeWindow(view.display, view.window_xid, x, y, width, height);
        if view.visible && !view.window.is_null() {
            gtk_widget_show_all(view.window);
            XMapRaised(view.display, view.window_xid);
        }
        XFlush(view.display);
    }
}

fn apply_bounds(view: &mut NativeWebView) {
    if view.destroyed || !view.created {
        return;
    }
    let (x, y, width, height) = scaled_bounds(view);
    unsafe {
        if !view.window.is_null() {
            gtk_widget_set_size_request(view.window, width as gint, height as gint);
            gtk_window_resize(view.window, width as gint, height as gint);
        }
        if !view.webview.is_null() {
            gtk_widget_set_size_request(view.webview, width as gint, height as gint);
        }
        if view.backend == Backend::WaylandSnapshot {
            if !view.window.is_null() {
                gtk_widget_show_all(view.window);
            }
            return;
        }
        if !view.display.is_null() && view.window_xid != 0 {
            XMoveResizeWindow(view.display, view.window_xid, x, y, width, height);
        }
        if view.visible && !view.window.is_null() {
            gtk_widget_show_all(view.window);
            if !view.display.is_null() && view.window_xid != 0 {
                XMapRaised(view.display, view.window_xid);
                XFlush(view.display);
            }
        }
    }
}

fn apply_visibility(view: &NativeWebView) {
    if view.destroyed || !view.created || view.window.is_null() {
        return;
    }
    unsafe {
        if view.backend == Backend::WaylandSnapshot {
            gtk_widget_show_all(view.window);
            return;
        }
        if view.visible {
            gtk_widget_show_all(view.window);
            if !view.display.is_null() && view.window_xid != 0 {
                XMapRaised(view.display, view.window_xid);
                XFlush(view.display);
            }
        } else {
            gtk_widget_hide(view.window);
        }
    }
}

fn scaled_bounds(view: &NativeWebView) -> (c_int, c_int, c_uint, c_uint) {
    let x = scale_to_i32(view.x, view.scale);
    let y = scale_to_i32(view.y, view.scale);
    let width = scale_to_i32(view.width.max(1), view.scale).max(1) as c_uint;
    let height = scale_to_i32(view.height.max(1), view.scale).max(1) as c_uint;
    (x, y, width, height)
}

fn evaluate_javascript(native: GtkNative, eval_id: jlong, script: String) {
    let webview = match native.lock() {
        Ok(view) if !view.destroyed && !view.webview.is_null() => view.webview,
        Ok(view) => {
            view.callbacks
                .on_evaluation_error(eval_id, "WebKitGTK webview is not ready".to_string());
            return;
        }
        Err(_) => return,
    };

    let script = match CString::new(script) {
        Ok(script) => script,
        Err(_) => {
            if let Ok(view) = native.lock() {
                view.callbacks
                    .on_evaluation_error(eval_id, "JavaScript contains a NUL byte".to_string());
            }
            return;
        }
    };
    let request = Box::new(EvalRequest { native, eval_id });
    unsafe {
        webkit_web_view_evaluate_javascript(
            webview as *mut WebKitWebView,
            script.as_ptr(),
            -1,
            ptr::null(),
            ptr::null(),
            ptr::null_mut(),
            Some(evaluate_javascript_finished),
            Box::into_raw(request) as gpointer,
        );
    }
}

fn evaluate_javascript_ignoring_result(native: GtkNative, script: String) {
    let webview = match native.lock() {
        Ok(view) if !view.destroyed && !view.webview.is_null() => view.webview,
        _ => return,
    };
    let Ok(script) = CString::new(script) else {
        return;
    };
    unsafe {
        webkit_web_view_evaluate_javascript(
            webview as *mut WebKitWebView,
            script.as_ptr(),
            -1,
            ptr::null(),
            ptr::null(),
            ptr::null_mut(),
            None,
            ptr::null_mut(),
        );
    }
}

struct EvalRequest {
    native: GtkNative,
    eval_id: jlong,
}

unsafe extern "C" fn evaluate_javascript_finished(
    source_object: *mut GObject,
    result: *mut GAsyncResult,
    user_data: gpointer,
) {
    let request = Box::from_raw(user_data as *mut EvalRequest);
    let mut error: *mut GError = ptr::null_mut();
    let value = webkit_web_view_evaluate_javascript_finish(
        source_object as *mut WebKitWebView,
        result,
        &mut error,
    );
    if value.is_null() {
        let message = take_gerror(error);
        if let Ok(view) = request.native.lock() {
            view.callbacks.on_evaluation_error(request.eval_id, message);
        }
        request_snapshot_later(request.native.clone(), 50);
        return;
    }

    let result = take_jsc_string(value);
    g_object_unref(value as gpointer);
    if let Ok(view) = request.native.lock() {
        view.callbacks.on_evaluation_result(request.eval_id, result);
    };
    request_snapshot_later(request.native.clone(), 50);
}

unsafe extern "C" fn script_message_received(
    _manager: *mut WebKitUserContentManager,
    result: *mut WebKitJavascriptResult,
    user_data: gpointer,
) {
    if result.is_null() || user_data.is_null() {
        return;
    }

    let native = Arc::from_raw(user_data as *const Mutex<NativeWebView>);
    let value = webkit_javascript_result_get_js_value(result);
    let raw = if value.is_null() {
        String::new()
    } else {
        take_jsc_string_borrowed(value)
    };
    if let Ok(view) = native.lock() {
        view.callbacks.on_message(raw);
    }
    let _ = Arc::into_raw(native);
}

unsafe extern "C" fn load_changed(_webview: *mut WebKitWebView, _event: gint, user_data: gpointer) {
    if user_data.is_null() {
        return;
    }

    let native = Arc::from_raw(user_data as *const Mutex<NativeWebView>);
    request_snapshot_later(native.clone(), 150);
    let _ = Arc::into_raw(native);
}

struct SnapshotRequest {
    native: GtkNative,
}

fn request_snapshot_later(native: GtkNative, delay_ms: guint) {
    unsafe {
        let request = Box::new(SnapshotRequest { native });
        g_timeout_add(
            delay_ms,
            Some(snapshot_timeout),
            Box::into_raw(request) as gpointer,
        );
    }
}

unsafe extern "C" fn snapshot_timeout(data: gpointer) -> gboolean {
    if !data.is_null() {
        let request = Box::from_raw(data as *mut SnapshotRequest);
        request_snapshot(request.native);
    }
    G_SOURCE_REMOVE
}

fn request_snapshot(native: GtkNative) {
    let webview = match native.lock() {
        Ok(mut view) => {
            if view.destroyed || !view.created || view.backend != Backend::WaylandSnapshot {
                return;
            }
            if view.webview.is_null() {
                log_snapshot_skip(&mut view, "webview is null");
                return;
            }
            if view.width <= 0 || view.height <= 0 {
                let width = view.width;
                let height = view.height;
                log_snapshot_skip(&mut view, format!("invalid bounds {width}x{height}"));
                return;
            }
            if view.snapshot_in_flight {
                view.snapshot_pending = true;
                log_snapshot_skip(&mut view, "already in flight");
                return;
            }
            view.snapshot_in_flight = true;
            view.webview
        }
        _ => return,
    };

    let request = Box::new(SnapshotRequest { native });
    unsafe {
        webkit_web_view_get_snapshot(
            webview as *mut WebKitWebView,
            WEBKIT_SNAPSHOT_REGION_VISIBLE,
            WEBKIT_SNAPSHOT_OPTIONS_NONE,
            ptr::null_mut(),
            Some(snapshot_finished),
            Box::into_raw(request) as gpointer,
        );
    }
}

fn log_snapshot_skip(view: &mut NativeWebView, reason: impl Into<String>) {
    view.snapshot_skip_count += 1;
    if view.snapshot_skip_count <= 5 || view.snapshot_skip_count % 25 == 0 {
        view.callbacks.on_log(
            0,
            format!(
                "LinuxWebKitGtkBridge: snapshot skipped #{}: {}",
                view.snapshot_skip_count,
                reason.into()
            ),
        );
    }
}

unsafe extern "C" fn snapshot_finished(
    source_object: *mut GObject,
    result: *mut GAsyncResult,
    user_data: gpointer,
) {
    let request = Box::from_raw(user_data as *mut SnapshotRequest);
    let mut error: *mut GError = ptr::null_mut();
    let surface = webkit_web_view_get_snapshot_finish(
        source_object as *mut WebKitWebView,
        result,
        &mut error,
    );

    let snapshot = if surface.is_null() {
        Err(take_gerror(error))
    } else {
        let snapshot = snapshot_pixels(surface);
        cairo_surface_destroy(surface);
        snapshot
    };

    let mut schedule_pending_snapshot = false;
    if let Ok(mut view) = request.native.lock() {
        view.snapshot_in_flight = false;
        schedule_pending_snapshot = view.snapshot_pending;
        view.snapshot_pending = false;
        if view.destroyed || view.backend != Backend::WaylandSnapshot {
            if view.destroy_pending {
                destroy_widgets(&mut view);
            }
            return;
        }
        match snapshot {
            Ok((width, height, pixels)) => {
                view.snapshot_count += 1;
                view.callbacks.on_snapshot(width, height, pixels);
            }
            Err(message) => {
                let level = if view.snapshot_count == 0 { 0 } else { 2 };
                view.callbacks.on_log(
                    level,
                    format!("LinuxWebKitGtkBridge: snapshot failed: {message}"),
                );
            }
        }
    };
    if schedule_pending_snapshot {
        request_snapshot_later(request.native.clone(), 50);
        request_snapshot_later(request.native.clone(), 250);
    }
}

unsafe fn snapshot_pixels(surface: *mut CairoSurface) -> BridgeResult<(jint, jint, Vec<jint>)> {
    let image = cairo_surface_map_to_image(surface, ptr::null());
    if image.is_null() {
        return Err("cairo_surface_map_to_image returned null".to_string());
    }

    cairo_surface_flush(image);
    let width = cairo_image_surface_get_width(image);
    let height = cairo_image_surface_get_height(image);
    let stride = cairo_image_surface_get_stride(image);
    let data = cairo_image_surface_get_data(image);
    if width <= 0 || height <= 0 || stride <= 0 || data.is_null() {
        cairo_surface_unmap_image(surface, image);
        return Err("snapshot surface has invalid dimensions".to_string());
    }

    let mut pixels = Vec::with_capacity((width * height) as usize);
    for y in 0..height {
        let row = data.add((y * stride) as usize);
        let row_data = slice::from_raw_parts(row, (width * 4) as usize);
        for x in 0..width as usize {
            let offset = x * 4;
            let b = row_data[offset] as jint;
            let g = row_data[offset + 1] as jint;
            let r = row_data[offset + 2] as jint;
            let a = row_data[offset + 3] as jint;
            pixels.push((a << 24) | (r << 16) | (g << 8) | b);
        }
    }

    cairo_surface_unmap_image(surface, image);
    Ok((width as jint, height as jint, pixels))
}

unsafe extern "C" fn drop_native_arc(data: gpointer, _closure: *mut GClosure) {
    if !data.is_null() {
        drop(Arc::from_raw(data as *const Mutex<NativeWebView>));
    }
}

fn run_with_handle<F>(env: &mut JNIEnv<'_>, handle: jlong, action: F)
where
    F: FnOnce(GtkNative) -> BridgeResult<()>,
{
    let Some(native) = clone_native_handle(env, handle) else {
        return;
    };
    if let Err(message) = action(native) {
        let _ = env.throw_new("java/lang/IllegalStateException", message);
    }
}

fn clone_native_handle(env: &mut JNIEnv<'_>, handle: jlong) -> Option<GtkNative> {
    if handle == 0 {
        let _ = env.throw_new(
            "java/lang/IllegalStateException",
            "WebKitGTK native handle is 0",
        );
        return None;
    }
    let handle = unsafe { (handle as *const NativeHandle).as_ref() };
    handle.map(|handle| handle.0.clone())
}

fn take_native_handle(_env: &mut JNIEnv<'_>, handle: jlong) -> Option<GtkNative> {
    if handle == 0 {
        return None;
    }
    let handle = unsafe { Box::from_raw(handle as *mut NativeHandle) };
    let native = handle.0.clone();
    drop(handle);
    Some(native)
}

fn with_locked_view<F>(native: &GtkNative, action: F)
where
    F: FnOnce(&mut NativeWebView),
{
    let Ok(mut view) = native.lock() else {
        return;
    };
    if view.destroyed {
        return;
    }
    action(&mut view);
}

static GTK_RUNTIME: std::sync::OnceLock<(Backend, BridgeResult<()>)> = std::sync::OnceLock::new();

fn ensure_gtk_runtime(backend: Backend) -> BridgeResult<()> {
    let (initialized_backend, result) =
        GTK_RUNTIME.get_or_init(|| (backend, start_gtk_thread(backend)));
    if *initialized_backend != backend {
        return Err(format!(
            "GTK runtime is already initialized for {}, cannot switch to {}",
            initialized_backend.gdk_backend_name(),
            backend.gdk_backend_name(),
        ));
    }
    result.clone()
}

fn ensure_gtk_runtime_initialized() -> BridgeResult<()> {
    GTK_RUNTIME
        .get()
        .map(|(_, result)| result.clone())
        .unwrap_or_else(|| ensure_gtk_runtime(Backend::WaylandSnapshot))
}

fn start_gtk_thread(backend: Backend) -> BridgeResult<()> {
    let (sender, receiver) = mpsc::channel();
    thread::Builder::new()
        .name("LinuxWebKitGTK".to_string())
        .spawn(move || {
            configure_runtime_environment(backend);
            let gdk_backend =
                CString::new(backend.gdk_backend_name()).expect("static backend name");
            unsafe {
                gdk_set_allowed_backends(gdk_backend.as_ptr());
            }
            let initialized = unsafe { gtk_init_check(ptr::null_mut(), ptr::null_mut()) != 0 };
            let _ = sender.send(if initialized {
                Ok(())
            } else {
                Err(format!(
                    "gtk_init_check failed; {} display or GTK initialization is unavailable",
                    backend.gdk_backend_name(),
                ))
            });
            if initialized {
                unsafe {
                    gtk_main();
                }
            }
        })
        .map_err(|error| format!("failed to start GTK thread: {error}"))?;
    receiver
        .recv_timeout(Duration::from_secs(10))
        .unwrap_or_else(|error| match error {
            mpsc::RecvTimeoutError::Timeout => Err(format!(
                "GTK thread did not initialize {} backend within 10 seconds",
                backend.gdk_backend_name()
            )),
            mpsc::RecvTimeoutError::Disconnected => {
                Err("GTK thread exited before initialization".to_string())
            }
        })
}

fn configure_runtime_environment(backend: Backend) {
    if backend != Backend::WaylandSnapshot {
        return;
    }
    set_default_env_var("WEBKIT_DISABLE_COMPOSITING_MODE", "1");
    set_default_env_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
}

fn set_default_env_var(key: &str, value: &str) {
    if env::var_os(key).is_none() {
        env::set_var(key, value);
    }
}

fn enqueue_gtk_task<F>(task: F) -> BridgeResult<()>
where
    F: FnOnce() + Send + 'static,
{
    ensure_gtk_runtime_initialized()?;
    let boxed: Box<Box<dyn FnOnce() + Send>> = Box::new(Box::new(task));
    unsafe {
        g_main_context_invoke_full(
            ptr::null_mut(),
            G_PRIORITY_DEFAULT,
            Some(run_gtk_task),
            Box::into_raw(boxed) as gpointer,
            None,
        );
    }
    Ok(())
}

fn enqueue_gtk_task_and_wait<F>(task: F) -> BridgeResult<()>
where
    F: FnOnce() + Send + 'static,
{
    let (sender, receiver) = mpsc::channel();
    enqueue_gtk_task(move || {
        task();
        let _ = sender.send(());
    })?;
    receiver
        .recv()
        .map_err(|error| format!("GTK task did not complete: {error}"))
}

unsafe extern "C" fn run_gtk_task(data: gpointer) -> gboolean {
    if !data.is_null() {
        let task = Box::from_raw(data as *mut Box<dyn FnOnce() + Send>);
        task();
    }
    G_SOURCE_REMOVE
}

fn jstring_to_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> BridgeResult<String> {
    env.get_string(&value)
        .map(|value| value.into())
        .map_err(format_jni_error)
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

unsafe fn take_jsc_string(value: *mut JSCValue) -> String {
    let string = take_jsc_string_borrowed(value);
    string
}

unsafe fn take_jsc_string_borrowed(value: *mut JSCValue) -> String {
    let text = jsc_value_to_string(value);
    if text.is_null() {
        return String::new();
    }
    take_gstring(text)
}

unsafe fn take_gstring(value: *mut c_char) -> String {
    let result = CStr::from_ptr(value).to_string_lossy().into_owned();
    g_free(value as gpointer);
    result
}

unsafe fn take_gerror(error: *mut GError) -> String {
    if error.is_null() {
        return "unknown WebKitGTK JavaScript evaluation error".to_string();
    }
    let message = if (*error).message.is_null() {
        format!(
            "WebKitGTK error domain={} code={}",
            (*error).domain,
            (*error).code
        )
    } else {
        CStr::from_ptr((*error).message)
            .to_string_lossy()
            .into_owned()
    };
    g_error_free(error);
    message
}

fn scale_to_i32(value: i32, scale: f64) -> i32 {
    ((value as f64) * scale).round() as i32
}

fn format_jni_error(error: jni::errors::Error) -> String {
    format!("{error:?}")
}
