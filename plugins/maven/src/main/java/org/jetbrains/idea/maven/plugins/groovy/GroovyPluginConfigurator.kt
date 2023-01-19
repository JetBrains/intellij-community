// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.*
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

class GroovyPluginConfigurator : MavenWorkspaceConfigurator {
  private val CONTRIBUTOR_EP = ExtensionPointName.create<PluginContributor>(
    "org.jetbrains.idea.maven.importing.groovy.foldersConfiguratorContributor")

  interface PluginContributor {
    fun getAdditionalPlugins(): List<GroovyPlugin>
  }

  @Suppress("DEPRECATION")
  interface GroovyPlugin {
    val groupId: String
    val artifactId: String
    val requiredDependency: PluginDependency?

    data class PluginDependency(val groupId: String, val artifactId: String)

    fun findInProject(mavenProject: MavenProject): MavenPlugin? {
      val plugin = mavenProject.findPlugin(groupId, artifactId)
      if (plugin == null) return null

      val dependency = requiredDependency
      if (dependency != null) {
        if (plugin.dependencies.none { id -> dependency.groupId == id.groupId && dependency.artifactId == id.artifactId }) {
          return null
        }
      }
      return plugin
    }
  }

  enum class KnownPlugins(override val groupId: String,
                          override val artifactId: String,
                          override val requiredDependency: GroovyPlugin.PluginDependency? = null) : GroovyPlugin {
    GROOVY_1_0("org.codehaus.groovy.maven", "gmaven-plugin"),
    GROOVY_1_1_PLUS("org.codehaus.gmaven", "gmaven-plugin"),
    GROOVY_GMAVEN("org.codehaus.gmaven", "groovy-maven-plugin"),
    GROOVY_GMAVEN_PLUS("org.codehaus.gmavenplus", "gmavenplus-plugin"),
    GROOVY_ECLIPSE_COMPILER("org.apache.maven.plugins", "maven-compiler-plugin",
                            GroovyPlugin.PluginDependency("org.codehaus.groovy", "groovy-eclipse-compiler"));

  }

  override fun getAdditionalSourceFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<String> {
    return getGroovySources(context, isForMain = true)
  }

  override fun getAdditionalTestSourceFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<String> {
    return getGroovySources(context, isForMain = false)
  }

  private fun getGroovySources(context: MavenWorkspaceConfigurator.FoldersContext, isForMain: Boolean): Stream<String> {
    return getGroovyPluginsInProject(context)
      .flatMap { collectGroovyFolders(it, isForMain) }
      .asStream()
  }

  override fun getFoldersToExclude(context: MavenWorkspaceConfigurator.FoldersContext): Stream<String> {
    return getGroovyPluginsInProject(context)
      .flatMap { collectIgnoredFolders(context.mavenProject, it) }
      .asStream()
  }

  private fun getGroovyPluginsInProject(context: MavenWorkspaceConfigurator.FoldersContext): Sequence<MavenPlugin> {
    val allPlugins = KnownPlugins.values().asSequence() +
                     (CONTRIBUTOR_EP.extensionList.stream().flatMap { it.getAdditionalPlugins().stream() }).asSequence()
    return allPlugins.mapNotNull { it.findInProject(context.mavenProject) }
  }

  companion object {
    @ApiStatus.Internal
    fun collectGroovyFolders(plugin: MavenPlugin, isForMain: Boolean): Collection<String> {
      val goal = if (isForMain) "compile" else "testCompile"
      val defaultDir = if (isForMain) "src/main/groovy" else "src/test/groovy"

      val dirs = MavenJDOMUtil.findChildrenValuesByPath(plugin.getGoalConfiguration(goal), "sources", "fileset.directory")
      return if (dirs.isEmpty()) listOf(defaultDir) else dirs
    }

    @ApiStatus.Internal
    fun collectIgnoredFolders(mavenProject: MavenProject, plugin: MavenPlugin): Collection<String> {
      val stubsDir = MavenJDOMUtil.findChildValueByPath(plugin.getGoalConfiguration("generateStubs"), "outputDirectory")
      val testStubsDir = MavenJDOMUtil.findChildValueByPath(plugin.getGoalConfiguration("generateTestStubs"), "outputDirectory")

      // exclude common parent of /groovy-stubs/main and /groovy-stubs/test
      val defaultStubsDir = mavenProject.getGeneratedSourcesDirectory(false) + "/groovy-stubs"
      return listOf(stubsDir ?: defaultStubsDir, testStubsDir ?: defaultStubsDir)
    }
  }
}