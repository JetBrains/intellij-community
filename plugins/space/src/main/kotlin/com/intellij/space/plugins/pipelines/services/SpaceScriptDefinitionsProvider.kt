package com.intellij.space.plugins.pipelines.services

import circlet.pipelines.config.utils.ScriptConstants
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.application.PathManager
import java.io.File
import java.nio.file.Paths
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class SpaceScriptDefinitionsProvider : ScriptDefinitionsProvider {

  override val id: String = "SpaceScriptDefinitionsProvider"

  override fun getDefinitionClasses(): Iterable<String> {
    return listOf(ScriptConstants.ScriptTemplateClassQualifiedName)
  }

  override fun getDefinitionsClassPath(): Iterable<File> {
    val jarName = "space-idea-script-definition.jar"
    if (PluginManagerCore.isRunningFromSources()) {
      return listOf(File("${PathManager.getHomePath()}/community/build/dependencies/build/space/$jarName"))
    }

    val plugin = PluginManagerCore.getPlugin(PluginId.getId("com.jetbrains.space"))
                 ?: throw IllegalStateException("Couldn't find space plugin descriptor")
    val jarPath = plugin.pluginPath.resolve(Paths.get("lib", jarName)).toFile()
    return listOf(jarPath)
  }

  override fun useDiscovery() = false
}
