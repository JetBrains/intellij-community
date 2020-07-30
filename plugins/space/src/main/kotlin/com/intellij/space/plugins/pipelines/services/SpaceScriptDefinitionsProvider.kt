package com.intellij.space.plugins.pipelines.services

import circlet.pipelines.config.utils.ScriptConstants
import com.intellij.space.plugins.pipelines.utils.JarFinder
import java.io.File
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class SpaceScriptDefinitionsProvider : ScriptDefinitionsProvider {

  override val id: String = "SpaceScriptDefinitionsProvider"

  override fun getDefinitionClasses(): Iterable<String> {
    return listOf(ScriptConstants.ScriptTemplateClassQualifiedName)
  }

  override fun getDefinitionsClassPath(): Iterable<File> {
    val defFile = JarFinder.find("pipelines-config-scriptdefinition-compile")
    if (!defFile.exists()) {
      throw Exception("File with ProjectScriptDefinition doesn't exist")
    }

    return listOf(defFile)
  }

  override fun useDiscovery(): Boolean {
    return true
  }
}
