// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview

import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.webview.impl.engine.WebView
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetRoot
import com.intellij.ui.webview.impl.engine.WebViewCreationOptions
import com.intellij.ui.webview.impl.engine.WebViewEngineAvailability
import com.intellij.ui.webview.impl.engine.WebViewEngineCapabilities
import com.intellij.ui.webview.impl.engine.WebViewEngineId
import com.intellij.ui.webview.impl.engine.WebViewEngineKind
import com.intellij.ui.webview.impl.engine.WebViewEngineRequirements
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.impl.engine.WebViewRuntime
import com.intellij.ui.webview.impl.engine.WebViewRuntimeInfo
import com.intellij.ui.webview.impl.engine.WebViewScriptResult
import com.intellij.ui.webview.impl.WebViewEngineBridge
import com.intellij.ui.webview.impl.WebViewJsMessageReceiver
import com.intellij.ui.webview.impl.engine.WebViewEngineCreationOptions
import com.intellij.ui.webview.impl.engine.WebViewEngineProvider
import com.intellij.ui.webview.impl.rpc.WebViewMessageBusImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class WebViewRuntimeTest {
  @Test
  fun createWebView_selectsProviderByPreferenceAndRequirements(): Unit = runBlocking {
    val rejected = FakeProvider(
      id = WebViewEngineId.SYSTEM_WINDOWS,
      capabilities = capabilities(assetServing = false),
      priority = 10,
    )
    val selected = FakeProvider(
      id = WebViewEngineId.JCEF,
      capabilities = capabilities(assetServing = true),
      priority = 20,
    )
    val runtime = WebViewRuntime().apply { providers = listOf(rejected, selected) }

    val webView = runtime.createWebView(
      scope = this,
      options = WebViewCreationOptions(
        requirements = WebViewEngineRequirements(assetServing = true),
      ),
    )

    assertEquals(WebViewEngineId.JCEF, webView.runtimeInfo.engineId)
    assertEquals(0, rejected.createCount)
    assertEquals(1, selected.createCount)
    webView.close()
  }

  @Test
  fun createWebView_reportsCapabilitiesWhenNoProviderSatisfiesRequirements(): Unit = runBlocking {
    val runtime = WebViewRuntime().apply {
      providers = listOf(
        FakeProvider(
          id = WebViewEngineId.SYSTEM_WINDOWS,
          capabilities = capabilities(assetServing = false),
          priority = 10,
        ),
      )
    }

    val error = runCatching {
      runtime.createWebView(
        scope = this,
        options = WebViewCreationOptions(
          requirements = WebViewEngineRequirements(assetServing = true),
        ),
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertTrue(error!!.message!!.contains("assetServing"), error.message)
  }

  @Test
  fun createWebView_continuesWhenProviderAvailabilityHasLinkageError(): Unit = runBlocking {
    val broken = FakeProvider(
      id = WebViewEngineId.SYSTEM_WINDOWS,
      capabilities = capabilities(assetServing = true),
      priority = 10,
      availabilityFailure = NoClassDefFoundError("com/intellij/ui/jcef/JBCefApp"),
    )
    val selected = FakeProvider(
      id = WebViewEngineId.JCEF,
      capabilities = capabilities(assetServing = true),
      priority = 20,
    )
    val runtime = WebViewRuntime().apply { providers = listOf(broken, selected) }

    val webView = runtime.createWebView(scope = this)

    assertEquals(WebViewEngineId.JCEF, webView.runtimeInfo.engineId)
    assertEquals(0, broken.createCount)
    assertEquals(1, selected.createCount)
    webView.close()
  }

  @Test
  fun createWebView_reportsProviderAvailabilityLinkageError(): Unit = runBlocking {
    val runtime = WebViewRuntime().apply {
      providers = listOf(
        FakeProvider(
          id = WebViewEngineId.JCEF,
          capabilities = capabilities(assetServing = true),
          priority = 10,
          availabilityFailure = NoClassDefFoundError("com/intellij/ui/jcef/JBCefApp"),
        ),
      )
    }

    val error = runCatching { runtime.createWebView(scope = this) }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertTrue(error!!.message!!.contains("availability check failed: java.lang.NoClassDefFoundError"), error.message)
    assertTrue(error.message!!.contains("com/intellij/ui/jcef/JBCefApp"), error.message)
  }

  @Test
  @RegistryKey(key = "ide.webview.engine", value = "JCEF")
  fun createWebView_appliesRegistryOverride(): Unit = runBlocking {
    val autoProvider = FakeProvider(
      id = WebViewEngineId.SYSTEM_MACOS,
      capabilities = capabilities(assetServing = true),
      priorities = mapOf(WebViewEngineKind.System to 10),
    )
    val overrideProvider = FakeProvider(
      id = WebViewEngineId.JCEF,
      displayName = "JCEF",
      capabilities = capabilities(assetServing = true),
      priorities = mapOf(WebViewEngineKind.Jcef to 10),
    )
    val runtime = WebViewRuntime().apply { providers = listOf(autoProvider, overrideProvider) }

    val webView = runtime.createWebView(scope = this)

    assertEquals(WebViewEngineId.JCEF, webView.runtimeInfo.engineId)
    assertEquals("JCEF", webView.runtimeInfo.displayName)
    assertEquals(0, autoProvider.createCount)
    assertEquals(1, overrideProvider.createCount)
    webView.close()
  }

  @Test
  @SystemProperty("ide.webview.debug.engine.overlay", "true")
  fun createWebView_exposesRuntimeInfoToCommonBridge(): Unit = runBlocking {
    val provider = FakeEngineProvider(
      id = WebViewEngineId.JCEF,
      displayName = "JCEF",
      capabilities = capabilities(assetServing = true),
    )
    val runtime = WebViewRuntime().apply { providers = listOf(provider) }

    val webView = runtime.createWebView(scope = this)

    val messageBus = webView.interop.messageBus as WebViewMessageBusImpl
    messageBus.transferFromJs("""{"jsonrpc":"2.0","method":"$RUNTIME_INFO_REQUEST_METHOD"}""")
    val delivered = withTimeout(5.seconds) { provider.engine.delivered.receive() }
    assertTrue(delivered.contains("\"method\":\"$RUNTIME_INFO_METHOD\""), delivered)
    assertTrue(delivered.contains("\"displayName\":\"JCEF\""), delivered)
    assertTrue(delivered.contains("\"overlayVisible\":true"), delivered)
    webView.close()
  }

  @Test
  fun createWebView_appendsThemeQueryToAssetLoads(): Unit = runBlocking {
    val provider = FakeEngineProvider(
      id = WebViewEngineId.JCEF,
      displayName = "JCEF",
      capabilities = capabilities(assetServing = true),
    )
    val runtime = WebViewRuntime().apply { providers = listOf(provider) }
    val assetRoot = WebViewAssetRoot.fromClasspath(WebViewRuntimeTest::class.java, WebViewAssetPath.of("webview/views/smoke"))

    val webView = runtime.createWebView(scope = this)
    webView.loadAsset(assetRoot, query = "foo=bar")

    val query = provider.engine.lastAssetQuery
    assertNotNull(query)
    assertTrue(query!!.startsWith("foo=bar&__webviewTheme="), query)
    webView.close()
  }

  @Test
  fun createWebView_exposesThemeToCommonBridge(): Unit = runBlocking {
    val provider = FakeEngineProvider(
      id = WebViewEngineId.JCEF,
      displayName = "JCEF",
      capabilities = capabilities(assetServing = true),
    )
    val runtime = WebViewRuntime().apply { providers = listOf(provider) }

    val webView = runtime.createWebView(scope = this)

    val messageBus = webView.interop.messageBus as WebViewMessageBusImpl
    messageBus.transferFromJs("""{"jsonrpc":"2.0","method":"$THEME_REQUEST_METHOD"}""")
    val delivered = withTimeout(5.seconds) { provider.engine.delivered.receive() }
    assertTrue(delivered.contains("\"method\":\"$THEME_CHANGED_METHOD\""), delivered)
    assertTrue(delivered.contains("\"theme\":"), delivered)
    assertTrue(delivered.contains("\"fonts\":"), delivered)
    assertTrue(delivered.contains("\"ui\":"), delivered)
    assertTrue(delivered.contains("\"editor\":"), delivered)
    assertTrue(delivered.contains("\"families\":"), delivered)
    assertTrue(delivered.contains("\"sizes\":"), delivered)
    assertTrue(delivered.contains("\"h0\":"), delivered)
    assertTrue(delivered.contains("\"medium\":"), delivered)
    assertTrue(delivered.contains("\"mini\":"), delivered)
    assertTrue(delivered.contains("\"ligatures\":"), delivered)
    webView.close()
  }

  @Test
  fun createWebView_propagatesHeavyweightCapabilityToCreatedWebView(): Unit = runBlocking {
    val heavyweightProvider = FakeEngineProvider(
      id = WebViewEngineId.SYSTEM_WINDOWS,
      displayName = "WebView2",
      capabilities = capabilities(assetServing = true),
      isHeavyweight = true,
    )
    val lightweightProvider = FakeEngineProvider(
      id = WebViewEngineId.JCEF,
      displayName = "JCEF",
      capabilities = capabilities(assetServing = true),
    )

    val heavyweightWebView = heavyweightProvider.createWebView(this, webViewEngineCreationOptions())
    val lightweightWebView = lightweightProvider.createWebView(this, webViewEngineCreationOptions())
    try {
      assertTrue(heavyweightWebView.isHeavyweight)
      assertFalse(lightweightWebView.isHeavyweight)
    }
    finally {
      heavyweightWebView.webView.close()
      lightweightWebView.webView.close()
    }
  }

  @Test
  fun createWebView_closesEngineWhenScopeCompletes(): Unit = runBlocking {
    val provider = FakeEngineProvider(
      id = WebViewEngineId.JCEF,
      displayName = "JCEF",
      capabilities = capabilities(assetServing = true),
    )
    val runtime = WebViewRuntime().apply { providers = listOf(provider) }
    @Suppress("RAW_SCOPE_CREATION")
    val webViewScope = CoroutineScope(SupervisorJob())
    runtime.createWebView(scope = webViewScope)

    webViewScope.coroutineContext.job.cancelAndJoin()

    assertEquals(1, provider.engine.closeCount)
  }

  @Test
  fun createPanel_requiresAssetServingAndCreatesProviderHostComponent(): Unit = runBlocking {
    val testScope = this
    val provider = FakeProvider(
      id = WebViewEngineId.JCEF,
      capabilities = capabilities(assetServing = true),
      priority = 10,
    )
    val runtime = WebViewRuntime().apply { providers = listOf(provider) }
    val assetRoot = WebViewAssetRoot.fromClasspath(WebViewRuntimeTest::class.java, WebViewAssetPath.of("webview/views/smoke"))

    val panel = withContext(Dispatchers.EDT) {
      runtime.createWebViewPanel(
        scope = testScope,
        options = WebViewPanelOptions(assetRoot = assetRoot),
      )
    }

    val webView = panel.webView as FakeWebView
    assertSame(webView, panel.webView)
    assertEquals(1, webView.loadAssetCount)
    assertEquals(1, provider.hostComponentCount)
    assertEquals(WebViewAssetPath.indexHtml(), webView.lastAssetPath)
    panel.webView.close()
  }

  @Test
  fun createPanel_closesEngineWhenScopeCompletes(): Unit = runBlocking {
    val provider = FakeEngineProvider(
      id = WebViewEngineId.JCEF,
      displayName = "JCEF",
      capabilities = capabilities(assetServing = true),
    )
    val runtime = WebViewRuntime().apply { providers = listOf(provider) }
    val assetRoot = WebViewAssetRoot.fromClasspath(WebViewRuntimeTest::class.java, WebViewAssetPath.of("webview/views/smoke"))
    @Suppress("RAW_SCOPE_CREATION")
    val webViewScope = CoroutineScope(SupervisorJob())
    try {
      withContext(Dispatchers.EDT) {
        runtime.createWebViewPanel(
          scope = webViewScope,
          options = WebViewPanelOptions(assetRoot = assetRoot),
        )
      }

      webViewScope.coroutineContext.job.cancelAndJoin()

      assertEquals(1, provider.engine.closeCount)
    }
    finally {
      webViewScope.cancel()
    }
  }

  private class FakeProvider(
    override val id: WebViewEngineId,
    override val displayName: String = id.value,
    override val capabilities: WebViewEngineCapabilities,
    private val priority: Int? = null,
    private val priorities: Map<WebViewEngineKind, Int> = emptyMap(),
    private val availability: WebViewEngineAvailability = WebViewEngineAvailability.Available,
    private val availabilityFailure: LinkageError? = null,
  ) : WebViewEngineProvider {
    var createCount = 0
      private set
    var hostComponentCount = 0
      private set

    override fun selectionPriority(preference: WebViewEngineKind): Int? = priorities[preference] ?: priority

    override fun availabilityBlocking(): WebViewEngineAvailability {
      availabilityFailure?.let { throw it }
      return availability
    }

    override suspend fun createWebView(webViewScope: CoroutineScope, options: WebViewEngineCreationOptions): WebViewEngineProvider.CreatedWebView {
      createCount++
      return FakeCreatedWebView(FakeWebView(WebViewRuntimeInfo(id, capabilities, displayName), webViewScope)) {
        hostComponentCount++
      }
    }

    override fun createEngine(scope: CoroutineScope, options: WebViewEngineCreationOptions): WebViewEngineBridge {
      error("Fake provider does not create engines")
    }

  }

  private class FakeCreatedWebView(
    override val webView: FakeWebView,
    private val onHostComponentCreated: () -> Unit,
  ) : WebViewEngineProvider.CreatedWebView {
    override val isHeavyweight: Boolean = false

    override fun createHostComponent(): JComponent {
      onHostComponentCreated()
      return JPanel()
    }
  }

  private class FakeEngineProvider(
    override val id: WebViewEngineId,
    override val displayName: String,
    override val capabilities: WebViewEngineCapabilities,
    isHeavyweight: Boolean = false,
  ) : WebViewEngineProvider {
    val engine = CapturingEngine(isHeavyweight)

    override fun selectionPriority(preference: WebViewEngineKind): Int? {
      return when (preference) {
        WebViewEngineKind.System, WebViewEngineKind.Jcef -> 10
        else -> null
      }
    }

    override fun availabilityBlocking(): WebViewEngineAvailability = WebViewEngineAvailability.Available

    override fun createEngine(scope: CoroutineScope, options: WebViewEngineCreationOptions): WebViewEngineBridge = engine
  }

  private class FakeWebView(
    override val runtimeInfo: WebViewRuntimeInfo,
    scope: CoroutineScope,
  ) : WebView {
    private val messageBus: WebViewMessageBusImpl = WebViewMessageBusImpl(scope, FakeEngine())
    override val interop = messageBus.interop
    var loadAssetCount = 0
      private set
    var lastAssetPath: WebViewAssetPath? = null
      private set

    override suspend fun loadFile(file: VirtualFile) {
    }

    override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
      loadAssetCount++
      lastAssetPath = entry
    }

    override suspend fun loadHtml(html: String) {
    }

    override suspend fun evaluateJavaScript(script: String): WebViewScriptResult = WebViewScriptResult(null)

    override suspend fun close() {
      messageBus.close()
    }
  }

  private class FakeEngine : WebViewEngineBridge {
    override val isHeavyweight: Boolean = false

    override suspend fun loadFile(file: Path) {
    }

    override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
    }

    override suspend fun loadHtml(html: String, baseFile: Path?) {
    }

    override suspend fun evaluateJavaScript(script: String): String? = null

    override suspend fun transferToJs(rawJson: String) {
      error("WebView message transport is not connected")
    }

    override fun connectMessageBus(receiver: WebViewJsMessageReceiver) {
    }

    override suspend fun close() {
    }

  }

  private class CapturingEngine(
    override val isHeavyweight: Boolean = false,
  ) : WebViewEngineBridge {
    val delivered = Channel<String>(Channel.UNLIMITED)
    var lastAssetQuery: String? = null
      private set
    var closeCount = 0
      private set

    override suspend fun loadFile(file: Path) {
    }

    override suspend fun loadAsset(root: WebViewAssetRoot, entry: WebViewAssetPath, query: String?) {
      lastAssetQuery = query
    }

    override suspend fun loadHtml(html: String, baseFile: Path?) {
    }

    override suspend fun evaluateJavaScript(script: String): String? = null

    override suspend fun transferToJs(rawJson: String) {
      delivered.send(rawJson)
    }

    override fun connectMessageBus(receiver: WebViewJsMessageReceiver) {
    }

    override suspend fun close() {
      closeCount++
      delivered.close()
    }
  }

  private fun capabilities(assetServing: Boolean): WebViewEngineCapabilities {
    return WebViewEngineCapabilities(
      assetServing = assetServing,
      messagePassing = true,
      swingEmbedding = true,
      interactiveInput = true,
    )
  }

  private fun webViewEngineCreationOptions(): WebViewEngineCreationOptions {
    return WebViewEngineCreationOptions(
      strictPreference = true,
      jcefNativeBundlePath = null,
      debugName = null,
    )
  }

  private companion object {
    const val RUNTIME_INFO_REQUEST_METHOD: String = "$/webview/runtimeInfoRequest"
    const val RUNTIME_INFO_METHOD: String = "$/webview/runtimeInfo"
    const val THEME_REQUEST_METHOD: String = "webview.theme/themeRequest"
    const val THEME_CHANGED_METHOD: String = "webview.theme/themeChanged"
  }
}
