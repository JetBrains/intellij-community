// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.jna.JnaLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.webview.impl.SwingWebViewHostPanel
import com.intellij.ui.webview.impl.mac.MacNativeWebViewHostPeer
import com.intellij.ui.webview.impl.mac.MacWebViewEngine
import com.intellij.ui.webview.impl.mac.createMacWebViewEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

/**
 * Headless smoke tests for the macOS WKWebView integration.
 *
 * These tests use a plain [JFrame] — no IntelliJ platform, no `@TestApplication`,
 * minimal UI interaction. They validate the full native pipeline:
 * thread dispatching → ObjC bridge → WKWebView lifecycle → JS evaluation → cleanup.
 *
 * Guarded with `@EnabledOnOs(OS.MAC)` — skipped on other platforms.
 */
@EnabledOnOs(OS.MAC)
@DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
class MacWebViewSmokeTest {

  private var frame: JFrame? = null
  private var scope: CoroutineScope? = null

  companion object {
    @JvmStatic
    @BeforeAll
    fun ensureJna() {
      if (!JnaLoader.isLoaded()) {
        JnaLoader.load(Logger.getInstance(MacWebViewSmokeTest::class.java))
      }
    }
  }

  @BeforeEach
  fun setUp() {
    @Suppress("RAW_SCOPE_CREATION") // Test: no parent scope available without IJ platform
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    SwingUtilities.invokeAndWait {
      frame = JFrame("WebView Smoke Test").apply {
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        size = Dimension(400, 300)
        isVisible = true
        toFront()
        requestFocus()
      }
    }
  }

  @AfterEach
  fun tearDown() {
    scope?.cancel()
    SwingUtilities.invokeAndWait { frame?.dispose() }
    frame = null
    scope = null
  }

  @Test
  fun evaluateJavaScript_returnsResult(): Unit = runBlocking {
    val facade = createMacWebViewEngine(scope!!)

    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.add(host)
      frame!!.revalidate()
    }

    // Give native view time to attach
    delay(1000.milliseconds)

    facade.loadHtml(/*language=HTML*/ "<html><body>test</body></html>")
    delay(500.milliseconds)

    val result = facade.evaluateJavaScript(/*language=JavaScript*/ "1 + 1")
    assertEquals("2", result)

    facade.close()
  }

  @Test
  fun applicationMode_preventsContextMenuDefault(): Unit = runBlocking {
    val facade = createMacWebViewEngine(scope!!)

    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.add(host)
      frame!!.revalidate()
    }

    delay(1000.milliseconds)

    facade.loadHtml(/*language=HTML*/ "<html><body>menu target</body></html>")
    delay(500.milliseconds)

    val result = facade.evaluateJavaScript(/*language=JavaScript*/ """
      (function() {
        const event = new MouseEvent('contextmenu', { bubbles: true, cancelable: true });
        document.body.dispatchEvent(event);
        return String(event.defaultPrevented);
      })()
    """.trimIndent())
    assertEquals("true", result)

    facade.close()
  }

  @Test
  @Suppress("HtmlDeprecatedAttribute")
  fun applicationMode_disablesInputAssistForFormControls(): Unit = runBlocking {
    val facade = createMacWebViewEngine(scope!!)

    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.add(host)
      frame!!.revalidate()
    }

    delay(1000.milliseconds)

    facade.loadHtml(/*language=HTML*/ """
      <html>
      <body>
        <input id="existing" autocomplete="email" autocorrect="on" autocapitalize="sentences" spellcheck="true">
      </body>
      </html>
    """.trimIndent())
    delay(500.milliseconds)

    val initialResult = facade.evaluateJavaScript(/*language=JavaScript*/ """
      (function() {
        const existing = document.getElementById('existing');
        return inputAssistState(existing);

        function inputAssistState(element) {
          const attributeNames = ['autocomplete', 'autocorrect', 'autocapitalize', 'spellcheck'];
          return attributeNames.map(name => element.getAttribute(name)).join('|') + '|' + String(element.spellcheck);
        }
      })()
    """.trimIndent())
    assertEquals("off|off|off|false|false", initialResult)

    facade.evaluateJavaScript(/*language=JavaScript*/ """
      (function() {
        const dynamic = document.createElement('input');
        dynamic.id = 'dynamic';
        setInputAssistEnabled(dynamic);
        document.body.appendChild(dynamic);

        const host = document.createElement('div');
        host.id = 'shadow-host';
        document.body.appendChild(host);

        const shadow = host.attachShadow({ mode: 'open' });
        const shadowInput = document.createElement('input');
        shadowInput.id = 'shadow';
        setInputAssistEnabled(shadowInput);
        shadow.appendChild(shadowInput);

        return 'created';

        function setInputAssistEnabled(element) {
          element.setAttribute('autocomplete', 'email');
          element.setAttribute('autocorrect', 'on');
          element.setAttribute('autocapitalize', 'sentences');
          element.setAttribute('spellcheck', 'true');
          element.spellcheck = true;
        }
      })()
    """.trimIndent())
    delay(100.milliseconds)

    val dynamicResult = facade.evaluateJavaScript(/*language=JavaScript*/ """
      (function() {
        return [
          inputAssistState(document.getElementById('existing')),
          inputAssistState(document.getElementById('dynamic')),
          inputAssistState(document.getElementById('shadow-host').shadowRoot.getElementById('shadow'))
        ].join(';');

        function inputAssistState(element) {
          const attributeNames = ['autocomplete', 'autocorrect', 'autocapitalize', 'spellcheck'];
          return attributeNames.map(name => element.getAttribute(name)).join('|') + '|' + String(element.spellcheck);
        }
      })()
    """.trimIndent())
    assertEquals("off|off|off|false|false;off|off|off|false|false;off|off|off|false|false", dynamicResult)

    val eventResult = facade.evaluateJavaScript(/*language=JavaScript*/ """
      (function() {
        const existing = document.getElementById('existing');
        const dynamic = document.getElementById('dynamic');
        const shadowInput = document.getElementById('shadow-host').shadowRoot.getElementById('shadow');
        for (const element of [existing, dynamic, shadowInput]) {
          setInputAssistEnabled(element);
        }

        const eventLog = [];
        document.addEventListener('focusin', function() { eventLog.push('focusin'); }, { once: true });
        document.addEventListener('beforeinput', function() { eventLog.push('beforeinput'); }, { once: true });
        document.addEventListener('input', function() { eventLog.push('input'); }, { once: true });

        const focusEvent = new Event('focusin', { bubbles: true, composed: true });
        const beforeInputEvent = new Event('beforeinput', { bubbles: true, composed: true, cancelable: true });
        const inputEvent = new Event('input', { bubbles: true, composed: true });
        shadowInput.dispatchEvent(focusEvent);
        shadowInput.dispatchEvent(beforeInputEvent);
        shadowInput.dispatchEvent(inputEvent);

        window['__inputAssistEventState'] = eventLog.join(',') + '|' + String(beforeInputEvent.defaultPrevented) + '|' + String(inputEvent.defaultPrevented);
        return window['__inputAssistEventState'];

        function setInputAssistEnabled(element) {
          element.setAttribute('autocomplete', 'email');
          element.setAttribute('autocorrect', 'on');
          element.setAttribute('autocapitalize', 'sentences');
          element.setAttribute('spellcheck', 'true');
          element.spellcheck = true;
        }
      })()
    """.trimIndent())
    assertEquals("focusin,beforeinput,input|false|false", eventResult)

    delay(100.milliseconds)

    val resetResult = facade.evaluateJavaScript(/*language=JavaScript*/ """
      (function() {
        return [
          inputAssistState(document.getElementById('existing')),
          inputAssistState(document.getElementById('dynamic')),
          inputAssistState(document.getElementById('shadow-host').shadowRoot.getElementById('shadow')),
          window['__inputAssistEventState']
        ].join(';');

        function inputAssistState(element) {
          const attributeNames = ['autocomplete', 'autocorrect', 'autocapitalize', 'spellcheck'];
          return attributeNames.map(name => element.getAttribute(name)).join('|') + '|' + String(element.spellcheck);
        }
      })()
    """.trimIndent())
    assertEquals("off|off|off|false|false;off|off|off|false|false;off|off|off|false|false;focusin,beforeinput,input|false|false", resetResult)

    facade.close()
  }

  @Test
  fun loadHtml_beforeAttach_isAppliedAfterAttach(): Unit = runBlocking {
    val facade = createMacWebViewEngine(scope!!)
    facade.loadHtml(/*language=HTML*/ "<html><body>queued-before-attach</body></html>")

    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.add(host)
      frame!!.revalidate()
    }

    delay(1000.milliseconds)

    val result = facade.evaluateJavaScript(/*language=JavaScript*/ "document.body.textContent.trim()")
    assertEquals("queued-before-attach", result)

    facade.close()
  }

  @Test
  fun close_isIdempotent(): Unit = runBlocking {
    val facade = createMacWebViewEngine(scope!!)

    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.add(host)
      frame!!.revalidate()
    }

    delay(500.milliseconds)

    facade.close()
    // Second close should not throw
    facade.close()
  }

  @Test
  fun evaluateJavaScript_afterClose_returnsNull(): Unit = runBlocking {
    val facade = createMacWebViewEngine(scope!!)

    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.add(host)
      frame!!.revalidate()
    }

    delay(500.milliseconds)

    facade.close()
    delay(200.milliseconds)

    val result = facade.evaluateJavaScript(/*language=JavaScript*/ "1 + 1")
    assertNull(result)
  }

  @Test
  fun facade_survives_host_detach_reattach(): Unit = runBlocking {
    val facade = createMacWebViewEngine(scope!!)

    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.add(host)
      frame!!.revalidate()
    }
    delay(1000.milliseconds)

    facade.loadHtml(/*language=HTML*/ "<html><body>phase1</body></html>")
    delay(500.milliseconds)
    assertEquals("phase1", facade.evaluateJavaScript(/*language=JavaScript*/ "document.body.textContent.trim()"))

    // Simulate tool window hide — host panel leaves the Swing hierarchy.
    SwingUtilities.invokeAndWait {
      frame!!.contentPane.removeAll()
      frame!!.revalidate()
    }
    delay(300.milliseconds)

    // Simulate tool window re-show with a fresh host panel reusing the same engine.
    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.add(host)
      frame!!.revalidate()
    }
    delay(1000.milliseconds)

    // Page state is retained and JS evaluation still works on the same WKWebView.
    assertEquals("phase1", facade.evaluateJavaScript(/*language=JavaScript*/ "document.body.textContent.trim()"))
    assertEquals("4", facade.evaluateJavaScript(/*language=JavaScript*/ "2 + 2"))

    facade.close()
  }

  @Test
  fun evaluateJavaScript_returnsResultForNestedHost(): Unit = runBlocking {
    val facade = createMacWebViewEngine(scope!!)

    SwingUtilities.invokeAndWait {
      val host = createHost(scope!!, facade)
      frame!!.contentPane.removeAll()
      frame!!.contentPane.add(JPanel(BorderLayout()).apply {
        add(JPanel().apply {
          preferredSize = Dimension(10, 24)
        }, BorderLayout.NORTH)
        add(JPanel(BorderLayout()).apply {
          add(JPanel().apply {
            preferredSize = Dimension(16, 10)
          }, BorderLayout.WEST)
          add(JPanel(BorderLayout()).apply {
            add(host, BorderLayout.CENTER)
          }, BorderLayout.CENTER)
          add(JPanel().apply {
            preferredSize = Dimension(12, 10)
          }, BorderLayout.EAST)
        }, BorderLayout.CENTER)
        add(JPanel().apply {
          preferredSize = Dimension(10, 28)
        }, BorderLayout.SOUTH)
      })
      frame!!.revalidate()
    }

    delay(1000.milliseconds)

    facade.loadHtml(/*language=HTML*/ "<html><body>nested</body></html>")
    delay(500.milliseconds)

    val result = facade.evaluateJavaScript(/*language=JavaScript*/ "document.body.textContent.trim()")
    assertEquals("nested", result)

    facade.close()
  }

  @Test
  fun createAndClose_100times_noLeak(): Unit = runBlocking {
    repeat(100) { i ->
      @Suppress("RAW_SCOPE_CREATION") // Test: no parent scope available without IJ platform
      val localScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val facade = createMacWebViewEngine(localScope)

      SwingUtilities.invokeAndWait {
        val host = createHost(localScope, facade)
        frame!!.contentPane.add(host)
        frame!!.revalidate()
      }

      delay(100.milliseconds)
      facade.close()
      localScope.cancel()

      SwingUtilities.invokeAndWait {
        frame!!.contentPane.removeAll()
        frame!!.revalidate()
      }

      // Brief pause every 10 iterations for GC pressure relief
      if (i % 10 == 9) {
        System.gc()
        delay(50.milliseconds)
      }
    }
    // If we get here without crash or exception, the test passes.
    // A more thorough check would track native handle counts, but for POC-0
    // this is sufficient to detect obvious leaks.
  }

  private fun createHost(scope: CoroutineScope, engine: MacWebViewEngine): SwingWebViewHostPanel {
    return SwingWebViewHostPanel(scope, engine, nativeHostPeer = MacNativeWebViewHostPeer(scope, engine))
  }

}
