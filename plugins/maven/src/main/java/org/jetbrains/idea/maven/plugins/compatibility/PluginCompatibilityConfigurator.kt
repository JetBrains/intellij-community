// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.compatibility

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenPluginWithArtifact
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSyncListener
import org.jetbrains.idea.maven.tasks.MavenCompilerTask
import org.jetbrains.idea.maven.tasks.MavenTasksManager
import org.jetbrains.idea.maven.tasks.MavenTasksManager.Phase
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.concurrent.ConcurrentHashMap


class PluginCompatibilityConfiguratorListener : MavenSyncListener {
  override fun syncFinished(project: Project) {
    if (MavenProjectsManager.getInstance(project).importingSettings.isRunPluginsCompatibilityOnSyncAndBuild) {
      MavenLog.LOG.info("Starting m2e compatibility parsing")
      project.service<PluginCompatibilityConfigurator>().configure()
    }
  }
}

@Service(Service.Level.PROJECT)
class PluginCompatibilityConfigurator(val project: Project) : MavenSyncListener {

  fun configure() {
    MavenCoroutineScopeProvider.getCoroutineScope(project).launch {
      configureAsync()
    }
  }

  suspend fun configureAsync() {
    val map = collectPluginsMap()


    val runAfterConfiguration = HashMap<MavenProject, MutableSet<String>>()
    val runBeforeBuild = HashMap<MavenProject, MutableSet<String>>()

    extractRunConfigurations(map, runAfterConfiguration, runBeforeBuild)

    runAllMavenConfigurations(runAfterConfiguration)
    setupBeforeCompileTasks(runBeforeBuild)
  }

  private fun extractRunConfigurations(
    map: ConcurrentHashMap<MavenProject, List<Pair<MavenId, MavenPluginM2ELifecycles>>>,
    runAfterConfiguration: HashMap<MavenProject, MutableSet<String>>,
    runBeforeBuild: HashMap<MavenProject, MutableSet<String>>,
  ) {
    map.forEach { prj, list ->
      list.forEach { (pluginId, lcs) ->
        val plugin = prj.findPlugin(pluginId.groupId, pluginId.artifactId, false)
        plugin?.executions?.forEach { execution ->
          execution.goals.forEach { g ->
            if (lcs.runOnConfiguration(g)) {
              runAfterConfiguration.compute(prj) { k, v ->
                (v
                 ?: HashSet()).also { it.add(toTaskGoal(lcs, g)) }
              }
            }
            if (lcs.runOnIncremental(g)) {
              runBeforeBuild.compute(prj) { k, v ->
                (v ?: HashSet()).also { it.add(toTaskGoal(lcs, g)) }
              }
            }
          }
        }

      }
    }
    if (MavenLog.LOG.isDebugEnabled) {
      MavenLog.LOG.debug("run on configuration: $runAfterConfiguration")
      MavenLog.LOG.debug("run on inc: $runBeforeBuild")
    }
  }

  private suspend fun collectPluginsMap(): ConcurrentHashMap<MavenProject, List<Pair<MavenId, MavenPluginM2ELifecycles>>> {
    val map = ConcurrentHashMap<MavenProject, List<Pair<MavenId, MavenPluginM2ELifecycles>>>()
    MavenProjectsManager.getInstance(project).projects.map { mavenProject ->
      MavenCoroutineScopeProvider.getCoroutineScope(project).async(Dispatchers.IO) {
        val list = mavenProject.pluginInfos.mapNotNull { pl -> getLifecycle(pl)?.let { pl.plugin.mavenId to it } }
        if (list.isNotEmpty()) {
          map[mavenProject] = list
        }
      }
    }.joinAll()
    return map
  }

  private fun toTaskGoal(lcs: MavenPluginM2ELifecycles, g: String?): String = "${lcs.prefix}:$g"

  private fun setupBeforeCompileTasks(runBeforeBuild: Map<MavenProject, Set<String>>) {
    val taskManager = MavenTasksManager.getInstance(project);
    val compileTasks = runBeforeBuild.flatMap { e ->
      e.value.map { MavenCompilerTask(e.key.path, it) }
    }
    taskManager.addCompileTasks(compileTasks, Phase.BEFORE_COMPILE)

  }

  private fun runAllMavenConfigurations(goalSet: Map<MavenProject, Set<String>>) {
    val projectsManager = MavenProjectsManager.getInstance(project)
    val explicitProfiles = projectsManager.getExplicitProfiles()
    goalSet.forEach { mavenProject, goals ->
      val params = MavenRunnerParameters(true,
                                         mavenProject.directory,
                                         mavenProject.file.getName(),
                                         goals.toList(),
                                         explicitProfiles.enabledProfiles,
                                         explicitProfiles.disabledProfiles)
      MavenRunConfigurationType.runConfiguration(project, params, null)
    }


  }

  fun getLifecycle(info: MavenPluginWithArtifact): MavenPluginM2ELifecycles? {
    val id = info.plugin.mavenId.stripVersion()
    if (knownPlugins.containsKey(id)) {
      return knownPlugins[id]
    }
    return info.artifact?.file?.toPath()?.let(MavenArtifactUtil::readPluginInfo)?.lifecycles
  }

  private fun MavenId.stripVersion() = "$groupId:$artifactId"

  companion object {
    private val knownPlugins: Map<String, MavenPluginM2ELifecycles?> = mapOf(
      "org.apache.maven.plugins:maven-clean-plugin" to null,
      "org.apache.maven.plugins:maven-compiler-plugin" to null,
      "org.apache.maven.plugins:maven-deploy-plugin" to null,
      "org.apache.maven.plugins:maven-install-plugin" to null,
      "org.apache.maven.plugins:maven-jar-plugin" to null,
      "org.apache.maven.plugins:maven-resources-plugin" to null,
      "org.apache.maven.plugins:maven-site-plugin" to null,
      "org.apache.maven.plugins:maven-surefire-plugin" to null
    )
  }
}