package com.intellij.space.plugins.pipelines.services

import circlet.pipelines.config.utils.ScriptConstants
import com.intellij.openapi.application.PathManager
import java.io.File
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class SpaceScriptDefinitionsProvider : ScriptDefinitionsProvider {

  override val id: String = "SpaceScriptDefinitionsProvider"

  override fun getDefinitionClasses(): Iterable<String> {
    return listOf(ScriptConstants.ScriptTemplateClassQualifiedName)
  }

  override fun getDefinitionsClassPath(): Iterable<File> {
    val jarName = "space-idea-script-definition.jar"
    return listOf(File("${PathManager.getHomePath()}/community/build/dependencies/build/space/$jarName"))
  }

  override fun useDiscovery(): Boolean {
    return true
  }
}
