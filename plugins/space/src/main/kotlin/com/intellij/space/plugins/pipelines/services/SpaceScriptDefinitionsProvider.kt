// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import java.io.File
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

private class SpaceScriptDefinitionsProvider : ScriptDefinitionsProvider {
  override val id = "SpaceScriptDefinitionsProvider"

  override fun getDefinitionClasses(): Iterable<String> = listOf()
  override fun getDefinitionsClassPath(): Iterable<File> = listOf()

  override fun provideDefinitions(
    baseHostConfiguration: ScriptingHostConfiguration,
    loadedScriptDefinitions: List<ScriptDefinition>
  ): Iterable<ScriptDefinition> = listOf(
    ScriptDefinition(
      SpaceKtsCompilationConfiguration(),
      ScriptEvaluationConfiguration.Default
    )
  )

  override fun useDiscovery() = false
}
