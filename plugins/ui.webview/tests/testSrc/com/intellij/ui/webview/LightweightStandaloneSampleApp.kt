// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.impl.engine.WebViewEngine
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.mac.MacNativeWebViewHostPeer
import com.intellij.ui.webview.impl.mac.MacWebViewEngine
import com.intellij.ui.webview.impl.mac.createMacWebViewEngine
import com.intellij.ui.webview.impl.windows.WinNativeWebViewHostPeer
import com.intellij.ui.webview.impl.windows.WinWebViewEngine
import com.intellij.ui.webview.impl.windows.createWinWebViewEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * Lightweight standalone sample app for manual smoke checks without plugin wiring.
 */
object LightweightStandaloneSampleApp {
  private const val RESOURCE_ROOT = "webview/views/sample-panel"
  private val ASSET_ROOT = WebViewAssetRoot.fromClasspath(LightweightStandaloneSampleApp::class.java, WebViewAssetPath.of(RESOURCE_ROOT))

  @JvmStatic
  fun main(args: Array<String>) {
    SwingUtilities.invokeLater {
      @Suppress("RAW_SCOPE_CREATION") // Standalone sample: no parent scope available.
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val facade = when {
        SystemInfo.isMac -> createMacWebViewEngine(scope)
        SystemInfo.isWindows -> createWinWebViewEngine(scope)
        else -> error("System WebView sample is supported only on macOS and Windows")
      }
      val nativeHostPeer = when {
        SystemInfo.isMac -> MacNativeWebViewHostPeer(scope, facade as MacWebViewEngine)
        SystemInfo.isWindows -> WinNativeWebViewHostPeer(facade as WinWebViewEngine)
        else -> error("System WebView sample is supported only on macOS and Windows")
      }
      val hostPanel = SwingWebViewHostPanel(scope, facade, nativeHostPeer = nativeHostPeer)
      val statusLabel = JLabel("Ready")

      val frame = JFrame("WebView Lightweight Standalone Sample").apply {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        setSize(900, 640)
        layout = BorderLayout()
        addWindowListener(object : WindowAdapter() {
          override fun windowClosing(event: WindowEvent) {
            scope.launch {
              facade.close()
              scope.cancel()
            }
          }
        })
      }

      val controls = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JButton("Reload sample HTML").apply {
          addActionListener { loadSampleFromResources(scope, facade, statusLabel) }
        })
        add(statusLabel)
      }

      frame.add(controls, BorderLayout.NORTH)
      frame.add(hostPanel, BorderLayout.CENTER)
      frame.isVisible = true

      loadSampleFromResources(scope, facade, statusLabel)
    }
  }

  private fun loadSampleFromResources(scope: CoroutineScope, facade: WebViewEngine, statusLabel: JLabel) {
    scope.launch {
      try {
        facade.loadAsset(ASSET_ROOT)
        SwingUtilities.invokeLater {
          statusLabel.text = "Loaded: $RESOURCE_ROOT"
        }
      }
      catch (t: Throwable) {
        facade.loadHtml(FALLBACK_HTML)
        SwingUtilities.invokeLater {
          statusLabel.text = "Fallback HTML loaded (${t::class.java.simpleName})"
        }
      }
    }
  }

  @Language("HTML")
  private val FALLBACK_HTML = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>WebView Lightweight Sample</title>
      <style>
        body { font-family: -apple-system, sans-serif; margin: 16px; background: #1e1e1e; color: #d4d4d4; }
        input, button { padding: 8px; margin-right: 8px; }
        .list { margin-top: 12px; max-height: 260px; overflow-y: auto; border: 1px solid #444; }
        .item { padding: 8px; border-bottom: 1px solid #333; }
      </style>
    </head>
    <body>
      <h2>WebView lightweight sample</h2>
      <input id="input" type="text" placeholder="Type here to test keyboard input">
      <button onclick="document.getElementById('status').textContent = document.getElementById('input').value || 'Empty input'">Read input</button>
      <span id="status">Ready</span>
      <div class="list" id="list"></div>
      <script>
        const list = document.getElementById('list');
        for (let i = 1; i <= 100; i++) {
          const item = document.createElement('div');
          item.className = 'item';
          item.textContent = 'Scrollable item ' + i;
          list.appendChild(item);
        }
      </script>
    </body>
    </html>
  """.trimIndent()
}
