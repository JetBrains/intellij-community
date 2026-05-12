// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.settings

import com.intellij.configurationStore.Property
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap

@State(
  name = "LspServerSettings",
  storages = [Storage("lspServers.xml")]
)
@Service(Service.Level.PROJECT)
internal class LspServerSettings : PersistentStateComponent<LspServerSettings> {
  @XCollection(style = XCollection.Style.v2)
  var servers: MutableList<LspServerConfiguration> = mutableListOf()

  override fun getState(): LspServerSettings = this

  override fun loadState(state: LspServerSettings) {
    XmlSerializerUtil.copyBean(state, this)
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
    return arguments.split(" ")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
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
