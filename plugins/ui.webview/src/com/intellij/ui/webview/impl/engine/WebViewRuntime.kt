// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.engine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.ui.webview.api.WebViewPanel
import com.intellij.ui.webview.api.WebViewPanelOptions
import com.intellij.ui.webview.impl.traceWebViewPerf
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.nio.file.Path
import java.util.MissingResourceException
import javax.swing.JPanel

private val LOG = logger<WebViewRuntime>()

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
    return LOG.traceWebViewPerf(
      "webview.runtime.createWebView.total",
      "preference=$preference, debugName=${options.debugName.orEmpty()}",
    ) {
      val provider = selectProvider(
        preference = preference,
        requirements = options.requirements,
      )
      LOG.traceWebViewPerf(
        "webview.runtime.createWebView.provider",
        "provider=${provider.id}, preference=$preference, debugName=${options.debugName.orEmpty()}",
      ) {
        provider.createWebView(
          webViewScope = scope,
          options = WebViewEngineCreationOptions(
            strictPreference = true,
            jcefNativeBundlePath = null,
            debugName = options.debugName,
            consoleLogCategory = options.consoleLogCategory,
          ),
        )
      }.webView
    }
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
    return LOG.traceWebViewPerf(
      "webview.panel.create.total",
      "viewId=${options.assetRoot.viewId}, debugName=${options.debugName.orEmpty()}",
    ) {
      try {
        val preference = resolveEnginePreference(WebViewEngineKind.System)
        val requirements = WebViewEngineRequirements(
          assetServing = true,
          messagePassing = true,
          swingEmbedding = true,
        )
        val provider = selectProvider(
          preference = preference,
          requirements = requirements,
        )
        val createdWebView = LOG.traceWebViewPerf(
          "webview.panel.provider.createWebView",
          panelDiagnosticDetails(options, preference, provider.id),
        ) {
          provider.createWebView(
            webViewScope = scope,
            options = WebViewEngineCreationOptions(
              strictPreference = true,
              jcefNativeBundlePath = null,
              debugName = options.debugName,
              consoleLogCategory = options.consoleLogCategory,
            ),
          )
        }
        val webViewInstance = createdWebView.webView
        webView = webViewInstance
        val hostComponent = LOG.traceWebViewPerf(
          "webview.panel.hostComponent.create",
          panelDiagnosticDetails(options, preference, provider.id),
        ) {
          createdWebView.createHostComponent()
        }
        val panelComponent = JPanel(BorderLayout()).apply {
          add(hostComponent, BorderLayout.CENTER)
        }
        val panel = WebViewPanel(webViewInstance, panelComponent) { webView ->
          webView.loadAsset(options.assetRoot, options.indexPath, options.query)
        }
        LOG.traceWebViewPerf(
          "webview.panel.firstReload",
          panelDiagnosticDetails(options, preference, provider.id),
        ) {
          panel.reload()
        }
        panel
      }
      catch (t: Throwable) {
        webView?.let { createdWebView ->
          runCatching { createdWebView.close() }
            .onFailure { closeFailure -> t.addSuppressed(closeFailure) }
        }
        throw t
      }
    }
  }

  private suspend fun selectProvider(
    preference: WebViewEngineKind,
    requirements: WebViewEngineRequirements,
  ): WebViewEngineProvider {
    val diagnostics = ArrayList<String>()
    val candidates = candidateProviders(preference)
    logSelectionStart(preference, requirements, candidates)
    return LOG.traceWebViewPerf(
      "webview.provider.select",
      "preference=$preference, requirements=$requirements, candidates=${candidates.size}",
    ) {
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
        when (val availability = LOG.traceWebViewPerf(
          "webview.provider.availability",
          "provider=${provider.id}, preference=$preference, priority=$priority",
        ) { availability(provider) }) {
          WebViewEngineAvailability.Available -> {
            logProviderSelected(preference, provider, priority)
            return@traceWebViewPerf provider
          }
          is WebViewEngineAvailability.Unavailable -> {
            diagnostics += "${provider.id} unavailable: ${availability.reason}"
            logProviderRejected(preference, provider, priority, "unavailable: ${availability.reason}")
          }
        }
      }
      failSelection(preference, requirements, diagnostics)
    }
  }

  private fun selectProviderBlocking(
    preference: WebViewEngineKind,
    requirements: WebViewEngineRequirements,
  ): WebViewEngineProvider {
    val diagnostics = ArrayList<String>()
    val candidates = candidateProviders(preference)
    logSelectionStart(preference, requirements, candidates)
    return LOG.traceWebViewPerf(
      "webview.provider.select.blocking",
      "preference=$preference, requirements=$requirements, candidates=${candidates.size}",
    ) {
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
        when (val availability = LOG.traceWebViewPerf(
          "webview.provider.availability.blocking",
          "provider=${provider.id}, preference=$preference, priority=$priority",
        ) { availabilityBlocking(provider) }) {
          WebViewEngineAvailability.Available -> {
            logProviderSelected(preference, provider, priority)
            return@traceWebViewPerf provider
          }
          is WebViewEngineAvailability.Unavailable -> {
            diagnostics += "${provider.id} unavailable: ${availability.reason}"
            logProviderRejected(preference, provider, priority, "unavailable: ${availability.reason}")
          }
        }
      }
      failSelection(preference, requirements, diagnostics)
    }
  }

  private fun panelDiagnosticDetails(
    options: WebViewPanelOptions,
    preference: WebViewEngineKind,
    providerId: WebViewEngineId,
  ): String {
    return "provider=$providerId, preference=$preference, viewId=${options.assetRoot.viewId}, " +
           "index=${options.indexPath}, debugName=${options.debugName.orEmpty()}"
  }

  private fun failSelection(
    preference: WebViewEngineKind,
    requirements: WebViewEngineRequirements,
    diagnostics: List<String>,
  ): Nothing {
    val message = buildSelectionFailureMessage(preference, requirements, diagnostics)
    LOG.warn("WebView engine applicability failed: $message")
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
    LOG.warn("WebView engine availability check failed for provider=${provider.id}", e)
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
    LOG.trace { "Checking WebView engine applicability: preference=$preference, requirements=$requirements, candidates=$candidateText" }
  }

  private fun logProviderRejected(
    preference: WebViewEngineKind,
    provider: WebViewEngineProvider,
    priority: Int,
    reason: String,
  ) {
    LOG.trace { "WebView engine applicability rejected: preference=$preference, provider=${provider.id}, priority=$priority, reason=$reason" }
  }

  private fun logProviderSelected(
    preference: WebViewEngineKind,
    provider: WebViewEngineProvider,
    priority: Int,
  ) {
    LOG.trace { "WebView engine applicability selected: preference=$preference, provider=${provider.id}, priority=$priority" }
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
