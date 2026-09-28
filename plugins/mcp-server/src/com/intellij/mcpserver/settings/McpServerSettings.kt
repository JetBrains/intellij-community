// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpServerFreshInstallPolicy
import com.intellij.mcpserver.settings.McpServerSettings.Companion.DEFAULT_MCP_PORT
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.PlatformUtils

/**
 * Whether the user allowed the MCP server to serve requests.
 *
 * A product that starts the server by default leaves the consent at [NOT_ASKED]. The first incoming call then asks the
 * user, see `McpServerConsentGate`. Every path that enables the server from the UI records [GRANTED], because that path
 * already shows the consent dialog.
 */
enum class McpServerConsent {
  NOT_ASKED,
  GRANTED,
  DENIED,
}

interface McpServerSettings {
  companion object {
    @JvmStatic
    fun getInstance(): McpServerSettings = service<McpServerSettingsImpl>()
    suspend fun getInstanceAsync(): McpServerSettings = serviceAsync<McpServerSettingsImpl>()

    @JvmStatic
    val DEFAULT_MCP_PORT: Int = BASE_MCP_PORT + getPortOffset()

    @JvmStatic
    val DEFAULT_MCP_PRIVATE_PORT: Int = DEFAULT_MCP_PORT + 100
  }

  var mcpServerPort: Int
  var enableMcpServer: Boolean
  var enableBraveMode: Boolean
  var enableTerminalAnsiHighlighting: Boolean
  var consent: McpServerConsent

  var enabledByFreshInstallPolicy: Boolean
}


@Service
@State(name = "McpServerSettings", storages = [Storage("mcpServer.xml")])
internal class McpServerSettingsImpl : McpServerSettings, SimplePersistentStateComponent<McpServerSettingsImpl.MyState>(MyState()) {

  private val portLock = Any()

  // Note that this `mcpServerPort` can be updated and read concurrently by multiple threads.
  // In order to avoid data races, we synchronize access to this field. It is not possible to use @Volatile here.
  override var mcpServerPort: Int
    get() = synchronized(portLock) {
      state.mcpServerPort
    }
    set(value) {
      synchronized(portLock) {
        state.mcpServerPort = value
      }
    }

  override var enableMcpServer: Boolean
    get() = state.enableMcpServer
    set(value) {
      state.enableMcpServer = value
    }

  override var enableBraveMode: Boolean
    get() = state.enableBraveMode
    set(value) {
      state.enableBraveMode = value
    }

  override var enableTerminalAnsiHighlighting: Boolean
    get() = state.enableTerminalAnsiHighlighting
    set(value) {
      state.enableTerminalAnsiHighlighting = value
    }

  override var consent: McpServerConsent
    get() = state.consent
    set(value) {
      state.consent = value
    }

  override var enabledByFreshInstallPolicy: Boolean
    get() = state.enabledByFreshInstallPolicy
    set(value) {
      state.enabledByFreshInstallPolicy = value
    }

  override fun noStateLoaded() {
    val enabled = McpServerFreshInstallPolicy.EP.extensionList.any { it.isServerEnabledOnFreshInstall() }
    state.enableMcpServer = enabled
    state.enabledByFreshInstallPolicy = enabled
  }

  override fun loadState(state: MyState) {
    super.loadState(state)
    if (state.enableMcpServer && state.consent == McpServerConsent.NOT_ASKED && !state.enabledByFreshInstallPolicy) {
      state.consent = McpServerConsent.GRANTED
    }
    val enabled = McpServerFreshInstallPolicy.EP.extensionList.any { it.isServerEnabledOnExistingInstall() }
    if (enabled && state.consent != McpServerConsent.DENIED) {
      state.enableMcpServer = true
      state.enabledByFreshInstallPolicy = state.consent == McpServerConsent.NOT_ASKED
    }
  }

  internal class MyState : BaseState() {
    var enableBraveMode: Boolean by property(false)
    var enableMcpServer: Boolean by property(false)
    var enableTerminalAnsiHighlighting: Boolean by property(false)
    var mcpServerPort: Int by property(DEFAULT_MCP_PORT)
    var consent: McpServerConsent by enum(McpServerConsent.NOT_ASKED)
    var enabledByFreshInstallPolicy: Boolean by property(false)
  }
}

private const val BASE_MCP_PORT: Int = 64342
private const val PORT_STEP: Int = 20
private fun getPortOffset(): Int {
  return when (PlatformUtils.getPlatformPrefix()) {
    PlatformUtils.IDEA_PREFIX -> 0
    PlatformUtils.CLION_PREFIX -> PORT_STEP * 1
    PlatformUtils.DBE_PREFIX -> PORT_STEP * 3
    PlatformUtils.GOIDE_PREFIX -> PORT_STEP * 4
    PlatformUtils.PHP_PREFIX -> PORT_STEP * 5
    PlatformUtils.PYCHARM_PREFIX -> PORT_STEP * 6
    PlatformUtils.RIDER_PREFIX -> PORT_STEP * 7
    PlatformUtils.RUBY_PREFIX -> PORT_STEP * 8
    PlatformUtils.RUSTROVER_PREFIX -> PORT_STEP * 9
    PlatformUtils.WEB_PREFIX -> PORT_STEP * 10
    // todo android studio
    else -> 0
  }
}
