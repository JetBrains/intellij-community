// Copyright 2000-2026 JetBrains s.r.o.
package com.intellij.mcpserver.impl

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.impl.ProjectUtil
import com.intellij.mcpserver.McpServerConsentUi
import com.intellij.mcpserver.settings.McpServerConsent
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.TestOnly

private val logger = logger<McpServerConsentGate>()

@Service
internal class McpServerConsentGate(
  private val consentUi: McpServerConsentUi? = serviceOrNull(),
) {
  private val askLock = Mutex()

  @Volatile
  @TestOnly
  internal var interactiveHostOverride: Boolean? = null

  private fun isInteractiveHost(): Boolean {
    interactiveHostOverride?.let { return it }
    val application = ApplicationManager.getApplication()
    return !application.isUnitTestMode && !application.isHeadlessEnvironment
  }

  suspend fun awaitConsent(): Boolean {
    if (isMcpServerForceEnabled()) return true
    if (!isInteractiveHost()) return true

    val settings = McpServerSettings.getInstanceAsync()
    decided(settings.consent)?.let { return it }

    return askLock.withLock {
      decided(settings.consent)?.let { return@withLock it }

      // No frontend, so nobody can agree. Refuse without recording it, because the user made no decision. A split-mode
      // backend lands here, and the user must then turn the server on in the settings of the frontend.
      val consentUi = consentUi ?: run {
        logger.warn("No ${McpServerConsentUi::class.simpleName} is available, so the MCP server call is rejected")
        return@withLock false
      }

      val granted = consentUi.askConsent(ProjectUtil.getActiveProject())
      applyDecision(settings, granted)
      granted
    }
  }

  private fun decided(consent: McpServerConsent): Boolean? = when (consent) {
    McpServerConsent.GRANTED -> true
    McpServerConsent.DENIED -> false
    McpServerConsent.NOT_ASKED -> null
  }

  private suspend fun applyDecision(settings: McpServerSettings, granted: Boolean) {
    settings.consent = if (granted) McpServerConsent.GRANTED else McpServerConsent.DENIED
    settings.enabledByFreshInstallPolicy = false
    if (!granted) {
      settings.enableMcpServer = false
      McpServerService.getInstanceIfCreated()?.scheduleResetToSettings()
    }
    if (ApplicationManager.getApplication().isUnitTestMode) return
    try {
      saveSettings(application, forceSavingAllSettings = true)
    }
    catch (t: Throwable) {
      if (t is CancellationException) {
        currentCoroutineContext().ensureActive()
      }
      logger.warn("Failed to persist the MCP server consent", t)
    }
  }
}
