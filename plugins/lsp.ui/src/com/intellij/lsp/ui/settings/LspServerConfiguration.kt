// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.settings

import com.intellij.configurationStore.Property
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.lsp.impl.LspPluginServerConfiguration
import com.intellij.platform.lsp.impl.LspServerSettingsProvider
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus

@State(
  name = "LspServerSettings",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.LOCAL)],
)
@Service(Service.Level.PROJECT)
@ApiStatus.Experimental
class LspServerSettings : PersistentStateComponent<LspServerSettings> {
  @XCollection(style = XCollection.Style.v2)
  internal var servers: MutableList<LspServerConfiguration> = mutableListOf()

  @get:XMap(entryTagName = "pluginServer", keyAttributeName = "id")
  private var pluginServers: MutableMap<String, PluginServerState> = LinkedHashMap()

  override fun getState(): LspServerSettings = this

  override fun loadState(state: LspServerSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun getPluginConfiguration(serverId: String): LspPluginServerConfiguration {
    val provider = LspServerSettingsProvider.EP_NAME.extensionList.firstOrNull { it.serverId == serverId }
                   ?: error("No LSP server settings provider is registered for '$serverId'")
    return getPluginConfiguration(provider)
  }

  internal fun getPluginConfiguration(provider: LspServerSettingsProvider): LspPluginServerConfiguration {
    val serverId = provider.serverId
    val state = pluginServers[serverId]
    return provider.defaultConfiguration.copy(
      arguments = state?.arguments?.toList() ?: provider.defaultConfiguration.arguments,
      filePatterns = state?.filePatterns?.toList() ?: provider.defaultConfiguration.filePatterns,
      initializationOptions = state?.initializationOptions ?: provider.defaultConfiguration.initializationOptions,
      environmentVariables = state?.environmentVariables?.get() ?: provider.defaultConfiguration.environmentVariables,
    )
  }

  internal fun updatePluginConfiguration(serverId: String, configuration: LspPluginServerConfiguration) {
    val state = pluginServers[serverId] ?: PluginServerState().also { pluginServers[serverId] = it }
    state.arguments = configuration.arguments.toMutableList()
    state.filePatterns = configuration.filePatterns.toMutableList()
    state.initializationOptions = configuration.initializationOptions
    state.environmentVariables.set(configuration.environmentVariables)
  }

  internal class PluginServerState {
    @get:XCollection(style = XCollection.Style.v2, elementName = "argument")
    var arguments: MutableList<String> = mutableListOf()

    @get:XCollection(style = XCollection.Style.v2, elementName = "pattern")
    var filePatterns: MutableList<String> = mutableListOf()

    @Tag("initializationOptions")
    var initializationOptions: String = ""

    var environmentVariables: EnvironmentVariablesDataOptions = EnvironmentVariablesDataOptions()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LspServerSettings = project.service()
  }
}

@Tag("server")
internal data class LspServerConfiguration(
  @NlsSafe @Attribute("name")
  var name: String = "",

  @Attribute("enabled")
  var enabled: Boolean = true,

  @Attribute("executablePath")
  var executablePath: String = "",

  var envVars: EnvironmentVariablesDataOptions = EnvironmentVariablesDataOptions(),

  @Tag("arguments")
  var arguments: String = "",

  @Tag("filePatterns")
  var filePatterns: String = "",

  @Tag("initializationOptions")
  var initializationOptions: String = "",

  @Attribute("communicationMode")
  var communicationMode: CommunicationMode = CommunicationMode.STDIO,

  @Attribute("socketPort")
  var socketPort: Int = 0,
) {
  enum class CommunicationMode {
    STDIO,
    SOCKET
  }

  fun getFileExtensions(): List<String> {
    return filePatterns.split(";")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { it.substringAfterLast(".") }
      .distinct()
  }
  
  fun getFileMatchers(): List<FileNameMatcher> {
    return filePatterns.split(";")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { LspPatternsPanel.parsePattern(it) }
  }

  fun getArgumentsList(): List<String> {
    return ParametersListUtil.parse(arguments)
  }
}

@Tag("")
internal class EnvironmentVariablesDataOptions : BaseState() {
  // user order of env must be preserved - do not sort user input
  @Property(description = "Environment variables")
  @get:XMap(entryTagName = "env", keyAttributeName = "key")
  val envs by linkedMap<String, String>()

  var isPassParentEnvs by property(true)

  fun set(envData: EnvironmentVariablesData) {
    envs.clear()
    envs.putAll(envData.envs)
    isPassParentEnvs = envData.isPassParentEnvs
    incrementModificationCount()
  }

  fun get(): EnvironmentVariablesData = EnvironmentVariablesData.create(envs, isPassParentEnvs)
}
