// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.ui.webview.api.WebViewPanel
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.impl.WebViewLogger
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.nio.file.Path
import java.util.MissingResourceException
import javax.swing.JPanel

@ApiStatus.Internal
@Service(Service.Level.APP)
class WebViewRuntime {
  private var providersOverride: List<WebViewEngineProvider>? = null

  internal var providers: List<WebViewEngineProvider>
    get() = providersOverride ?: (defaultWebViewEngineProviders() + WebViewEngineProvider.EP_NAME.extensionList)
    set(value) {
      providersOverride = value
    }

  suspend fun createWebView(
    scope: CoroutineScope,
    options: WebViewCreationOptions = WebViewCreationOptions(),
  ): WebView {
    val preference = resolveEnginePreference(options.engineKind)
    val provider = selectProvider(
      preference = preference,
      requirements = options.requirements,
    )
    return provider.createWebView(
      webViewScope = scope,
      options = WebViewEngineCreationOptions(
        strictPreference = true,
        jcefNativeBundlePath = null,
        debugName = options.debugName,
        consoleLogCategory = options.consoleLogCategory,
      ),
    ).webView
  }

  internal fun createEngine(
    scope: CoroutineScope,
    engineKind: WebViewEngineKind = WebViewEngineKind.System,
    jcefNativeBundlePath: Path? = null,
  ): WebViewEngine {
    return createEngine(
      scope = scope,
      preference = resolveEnginePreference(engineKind),
      strictPreference = true,
      jcefNativeBundlePath = jcefNativeBundlePath,
    )
  }

  internal fun createEngine(
    scope: CoroutineScope,
    preference: WebViewEngineKind,
    strictPreference: Boolean,
    jcefNativeBundlePath: Path? = null,
  ): WebViewEngine {
    val provider = selectProviderBlocking(
      preference = preference,
      requirements = WebViewEngineRequirements(),
    )
    return provider.createEngine(
      scope = scope,
      options = WebViewEngineCreationOptions(
        strictPreference = strictPreference,
        jcefNativeBundlePath = jcefNativeBundlePath,
        debugName = null,
      ),
    )
  }

  @RequiresEdt
  internal suspend fun createWebViewPanel(
    scope: CoroutineScope,
    options: WebViewPanelOptions,
  ): WebViewPanel {
    var webView: WebView? = null
    try {
      val preference = resolveEnginePreference(WebViewEngineKind.System)
      val provider = selectProvider(
        preference = preference,
        requirements = WebViewEngineRequirements(
          assetServing = true,
          messagePassing = true,
          swingEmbedding = true,
        ),
      )
      val createdWebView = provider.createWebView(
        webViewScope = scope,
        options = WebViewEngineCreationOptions(
          strictPreference = true,
          jcefNativeBundlePath = null,
          debugName = options.debugName,
          consoleLogCategory = options.consoleLogCategory,
        ),
      )
      val webViewInstance = createdWebView.webView
      webView = webViewInstance
      val hostComponent = createdWebView.createHostComponent()
      val panelComponent = JPanel(BorderLayout()).apply {
        add(hostComponent, BorderLayout.CENTER)
      }
      val panel = WebViewPanel(webViewInstance, panelComponent) { webView ->
        webView.loadAsset(options.assetRoot, options.indexPath, options.query)
      }
      panel.reload()
      return panel
    }
    catch (t: Throwable) {
      webView?.let { createdWebView ->
        runCatching { createdWebView.close() }
          .onFailure { closeFailure -> t.addSuppressed(closeFailure) }
      }
      throw t
    }
  }

  private suspend fun selectProvider(
    preference: WebViewEngineKind,
    requirements: WebViewEngineRequirements,
  ): WebViewEngineProvider {
    val diagnostics = ArrayList<String>()
    val candidates = candidateProviders(preference)
    logSelectionStart(preference, requirements, candidates)
    if (candidates.isEmpty()) {
      diagnostics += "no candidate providers"
    }
    for ((provider, priority) in candidates) {
      val missingRequirements = provider.capabilities.missingRequirements(requirements)
      if (missingRequirements.isNotEmpty()) {
        diagnostics += "${provider.id} rejected: missing ${missingRequirements.joinToString()}"
        logProviderRejected(preference, provider, priority, "missing ${missingRequirements.joinToString()}")
        continue
      }
      when (val availability = availability(provider)) {
        WebViewEngineAvailability.Available -> {
          logProviderSelected(preference, provider, priority)
          return provider
        }
        is WebViewEngineAvailability.Unavailable -> {
          diagnostics += "${provider.id} unavailable: ${availability.reason}"
          logProviderRejected(preference, provider, priority, "unavailable: ${availability.reason}")
        }
      }
    }
    failSelection(preference, requirements, diagnostics)
  }

  private fun selectProviderBlocking(
    preference: WebViewEngineKind,
    requirements: WebViewEngineRequirements,
  ): WebViewEngineProvider {
    val diagnostics = ArrayList<String>()
    val candidates = candidateProviders(preference)
    logSelectionStart(preference, requirements, candidates)
    if (candidates.isEmpty()) {
      diagnostics += "no candidate providers"
    }
    for ((provider, priority) in candidates) {
      val missingRequirements = provider.capabilities.missingRequirements(requirements)
      if (missingRequirements.isNotEmpty()) {
        diagnostics += "${provider.id} rejected: missing ${missingRequirements.joinToString()}"
        logProviderRejected(preference, provider, priority, "missing ${missingRequirements.joinToString()}")
        continue
      }
      when (val availability = availabilityBlocking(provider)) {
        WebViewEngineAvailability.Available -> {
          logProviderSelected(preference, provider, priority)
          return provider
        }
        is WebViewEngineAvailability.Unavailable -> {
          diagnostics += "${provider.id} unavailable: ${availability.reason}"
          logProviderRejected(preference, provider, priority, "unavailable: ${availability.reason}")
        }
      }
    }
    failSelection(preference, requirements, diagnostics)
  }

  private fun failSelection(
    preference: WebViewEngineKind,
    requirements: WebViewEngineRequirements,
    diagnostics: List<String>,
  ): Nothing {
    val message = buildSelectionFailureMessage(preference, requirements, diagnostics)
    WebViewLogger.LOG.warn("WebView engine applicability failed: $message")
    error(message)
  }

  private suspend fun availability(provider: WebViewEngineProvider): WebViewEngineAvailability {
    return try {
      provider.availability()
    }
    catch (e: LinkageError) {
      availabilityFailed(provider, e)
    }
  }

  private fun availabilityBlocking(provider: WebViewEngineProvider): WebViewEngineAvailability {
    return try {
      provider.availabilityBlocking()
    }
    catch (e: LinkageError) {
      availabilityFailed(provider, e)
    }
  }

  private fun availabilityFailed(provider: WebViewEngineProvider, e: LinkageError): WebViewEngineAvailability.Unavailable {
    val reason = buildString {
      append("availability check failed: ")
      append(e.javaClass.name)
      e.message?.let { message ->
        append(": ")
        append(message)
      }
    }
    WebViewLogger.LOG.warn("WebView engine availability check failed for provider=${provider.id}", e)
    return WebViewEngineAvailability.Unavailable(reason)
  }

  private fun candidateProviders(preference: WebViewEngineKind): List<Pair<WebViewEngineProvider, Int>> {
    return providers.mapNotNull { provider ->
      val priority = provider.selectionPriority(preference) ?: return@mapNotNull null
      provider to priority
    }.sortedBy { it.second }
  }

  private fun logSelectionStart(
    preference: WebViewEngineKind,
    requirements: WebViewEngineRequirements,
    candidates: List<Pair<WebViewEngineProvider, Int>>,
  ) {
    val candidateText = candidates.joinToString { (provider, priority) ->
      "${provider.id}(priority=$priority, capabilities=${provider.capabilities})"
    }.ifEmpty { "none" }
    WebViewLogger.LOG.info("Checking WebView engine applicability: preference=$preference, requirements=$requirements, candidates=$candidateText")
  }

  private fun logProviderRejected(
    preference: WebViewEngineKind,
    provider: WebViewEngineProvider,
    priority: Int,
    reason: String,
  ) {
    WebViewLogger.LOG.info("WebView engine applicability rejected: preference=$preference, provider=${provider.id}, priority=$priority, reason=$reason")
  }

  private fun logProviderSelected(
    preference: WebViewEngineKind,
    provider: WebViewEngineProvider,
    priority: Int,
  ) {
    WebViewLogger.LOG.info("WebView engine applicability selected: preference=$preference, provider=${provider.id}, priority=$priority")
  }

  private fun resolveEnginePreference(requestedPreference: WebViewEngineKind): WebViewEngineKind {
    return readRegistryEnginePreference() ?: requestedPreference
  }

  private fun buildSelectionFailureMessage(
    preference: WebViewEngineKind,
    requirements: WebViewEngineRequirements,
    diagnostics: List<String>,
  ): String {
    return buildString {
      append("No WebView engine satisfies preference=")
      append(preference)
      append(", requirements=")
      append(requirements)
      if (diagnostics.isNotEmpty()) {
        append(". Diagnostics: ")
        append(diagnostics.joinToString("; "))
      }
    }
  }

  private fun readRegistryEnginePreference(): WebViewEngineKind? {
    val value = readRegistryEngineOverrideValue()?.trim() ?: return null
    return when (value.uppercase()) {
      "", "SYSTEM" -> WebViewEngineKind.System
      "JCEF" -> WebViewEngineKind.Jcef
      else -> error("Unsupported $WEBVIEW_ENGINE_REGISTRY_KEY value '$value'. Expected SYSTEM or JCEF")
    }
  }

  private fun readRegistryEngineOverrideValue(): String? {
    return try {
      val registryValue = RegistryManager.getInstance().get(WEBVIEW_ENGINE_REGISTRY_KEY)
      registryValue.selectedOption ?: registryValue.asString()
    }
    catch (_: MissingResourceException) {
      null
    }
  }

  companion object {
    private const val WEBVIEW_ENGINE_REGISTRY_KEY = "ide.webview.engine"

    @JvmStatic
    fun getInstance(): WebViewRuntime = service()
  }
}
