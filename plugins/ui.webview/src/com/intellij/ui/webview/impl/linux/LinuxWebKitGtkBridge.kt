// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.linux

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.webview.impl.NativeBridgeLibrary
import com.intellij.ui.webview.impl.webViewNativeArchDirectory
import org.jetbrains.annotations.ApiStatus

internal val linuxWebKitGtkBridgeLibrary = NativeBridgeLibrary(
  displayName = "Linux WebKitGTK bridge library",
  logEvent = "linux-webkitgtk-load",
  relativePaths = listOf(
    "lib/webview-native/linux/${webViewNativeArchDirectory()}/libLinuxWebKitGtkBridge.so",
    "lib/webview-native/linux/${webViewNativeArchDirectory()}/liblinux_webkitgtk_bridge.so",
  ),
  rebuildHint = "Rebuild community/platform/ui.webview/native/LinuxWebKitGtkBridge.",
  loadFailureHint = "The Linux WebView backend requires GTK3 and WebKitGTK 4.1 runtime libraries. " +
                    "Rebuild community/platform/ui.webview/native/LinuxWebKitGtkBridge.",
  pluginAnchorClass = LinuxWebKitGtkBridgePluginAnchor::class.java,
)

private class LinuxWebKitGtkBridgePluginAnchor

@ApiStatus.Internal
internal object LinuxWebKitGtkBridge {
  private const val EXPECTED_NATIVE_ABI_VERSION = "wvi-linux-webkitgtk-v1"

  init {
    if (SystemInfo.isLinux) {
      loadNativeLibrary()
    }
  }

  @JvmStatic
  private external fun abiVersionNative(): String

  @JvmStatic
  private external fun createNative(parentWindowHandle: Long, backend: Int, callbacks: Callbacks): Long

  @JvmStatic
  private external fun destroyNative(handle: Long)

  @JvmStatic
  private external fun attachToParentNative(handle: Long, parentWindowHandle: Long)

  @JvmStatic
  private external fun detachNative(handle: Long)

  @JvmStatic
  private external fun setBoundsNative(handle: Long, x: Int, y: Int, width: Int, height: Int, scale: Double)

  @JvmStatic
  private external fun setVisibleNative(handle: Long, visible: Boolean)

  @JvmStatic
  private external fun focusNative(handle: Long)

  @JvmStatic
  private external fun clearFocusNative(handle: Long)

  @JvmStatic
  private external fun loadUrlNative(handle: Long, url: String)

  @JvmStatic
  private external fun loadHtmlNative(handle: Long, html: String, baseUrl: String?)

  @JvmStatic
  private external fun evaluateJavaScriptNative(handle: Long, evalId: Long, script: String)

  @JvmStatic
  private external fun transferToJsNative(handle: Long, rawJson: String)

  @JvmStatic
  private external fun shutdownRuntimeNative()

  fun create(parentWindowHandle: Long, backend: LinuxWebKitBackend, callbacks: Callbacks): Long = createNative(parentWindowHandle, backend.nativeId, callbacks)
  fun destroy(handle: Long) = destroyNative(handle)
  fun attachToParent(handle: Long, parentWindowHandle: Long) = attachToParentNative(handle, parentWindowHandle)
  fun detach(handle: Long) = detachNative(handle)
  fun setBounds(handle: Long, x: Int, y: Int, width: Int, height: Int, scale: Double) = setBoundsNative(handle, x, y, width, height, scale)
  fun setVisible(handle: Long, visible: Boolean) = setVisibleNative(handle, visible)
  fun focus(handle: Long) = focusNative(handle)
  fun clearFocus(handle: Long) = clearFocusNative(handle)
  fun loadUrl(handle: Long, url: String) = loadUrlNative(handle, url)
  fun loadHtml(handle: Long, html: String, baseUrl: String?) = loadHtmlNative(handle, html, baseUrl)
  fun evaluateJavaScript(handle: Long, evalId: Long, script: String) = evaluateJavaScriptNative(handle, evalId, script)
  fun transferToJs(handle: Long, rawJson: String) = transferToJsNative(handle, rawJson)
  fun shutdownRuntimeForTests() = shutdownRuntimeNative()

  private fun loadNativeLibrary() {
    val libraryPath = linuxWebKitGtkBridgeLibrary.load()
    linuxWebKitGtkBridgeLibrary.verifyAbi(libraryPath, EXPECTED_NATIVE_ABI_VERSION, ::abiVersionNative)
  }

  internal interface Callbacks {
    fun onCreated(handle: Long)
    fun onCreateFailed(message: String)
    fun onMessage(raw: String)
    fun onEvaluationResult(evalId: Long, result: String?)
    fun onEvaluationError(evalId: Long, message: String)
    fun onSnapshot(width: Int, height: Int, pixels: IntArray)
    fun onLog(level: Int, message: String)
  }
}
