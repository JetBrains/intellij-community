// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import org.jetbrains.idea.maven.importing.MavenProjectImporterUtil.LegacyExtensionImporter.CountAndTime
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.statistics.MavenImportCollector
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal object MavenProjectImporterUtil {
  fun importLegacyExtensions(project: Project,
                             modifiableModelsProvider: IdeModifiableModelsProvider,
                             extensionImporters: List<LegacyExtensionImporter>,
                             postTasks: List<MavenProjectsProcessorTask>,
                             activity: StructuredIdeActivity) {
    val importers = extensionImporters.filter { !it.isModuleDisposed }
    if (importers.isEmpty()) return
    val beforeBridgesCreation = System.nanoTime()
    // commit does nothing for this provider, so it should be reused
    val provider = modifiableModelsProvider as? IdeUIModifiableModelsProvider
                   ?: ProjectDataManager.getInstance().createModifiableModelsProvider(project)
    var bridgesCreationNano = System.nanoTime() - beforeBridgesCreation
    try {
      val beforeInitInit = System.nanoTime()
      importers.forEach(Consumer { importer: LegacyExtensionImporter -> importer.init(provider) })
      bridgesCreationNano += System.nanoTime() - beforeInitInit
      val counters: MutableMap<Class<out MavenImporter?>, CountAndTime> = HashMap()
      importers.forEach(Consumer { it.preConfig(counters) })
      importers.forEach(Consumer { it.config(postTasks, counters) })
      importers.forEach(Consumer { it.postConfig(counters) })
      for ((key, value) in counters) {
        MavenImportCollector.IMPORTER_RUN.log(
          project,
          MavenImportCollector.ACTIVITY_ID.with(activity),
          MavenImportCollector.IMPORTER_CLASS.with(key),
          MavenImportCollector.NUMBER_OF_MODULES.with(value.count),
          MavenImportCollector.TOTAL_DURATION_MS.with(TimeUnit.NANOSECONDS.toMillis(value.timeNano)))
      }
    }
    finally {
      val beforeCommit = System.nanoTime()
      MavenUtil.invokeAndWaitWriteAction(project) {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring { provider.commit() }
      }
      val afterCommit = System.nanoTime()
      MavenImportCollector.LEGACY_IMPORTERS_STATS.log(
        project,
        MavenImportCollector.ACTIVITY_ID.with(activity),
        MavenImportCollector.DURATION_OF_LEGACY_BRIDGES_CREATION_MS.with(TimeUnit.NANOSECONDS.toMillis(bridgesCreationNano)),
        MavenImportCollector.DURATION_OF_LEGACY_BRIDGES_COMMIT_MS.with(TimeUnit.NANOSECONDS.toMillis(afterCommit - beforeCommit)))
    }
  }

  @JvmStatic
  fun selectScope(mavenScope: String?): DependencyScope {
    if (MavenConstants.SCOPE_RUNTIME == mavenScope) return DependencyScope.RUNTIME
    if (MavenConstants.SCOPE_TEST == mavenScope) return DependencyScope.TEST
    return if (MavenConstants.SCOPE_PROVIDED == mavenScope) DependencyScope.PROVIDED else DependencyScope.COMPILE
  }

  class LegacyExtensionImporter private constructor(private val myModule: Module,
                                                    private val myMavenProjectsTree: MavenProjectsTree,
                                                    private val myMavenProject: MavenProject,
                                                    private val myMavenProjectChanges: MavenProjectChanges,
                                                    private val myMavenProjectToModuleName: Map<MavenProject, String>,
                                                    private val myImporters: List<MavenImporter>) {
    private var myRootModelAdapter: MavenRootModelAdapter? = null
    private var myModifiableModelsProvider: IdeModifiableModelsProvider? = null
    val isModuleDisposed: Boolean
      get() = myModule.isDisposed()

    fun init(ideModelsProvider: IdeModifiableModelsProvider) {
      myModifiableModelsProvider = ideModelsProvider
      myRootModelAdapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(myMavenProject, myModule, myModifiableModelsProvider))
    }

    private fun doConfigurationStep(step: Runnable) {
      MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), step)
    }

    fun preConfig(counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      doConfigurationStep { doPreConfig(counters) }
    }

    private fun doPreConfig(counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      if (myModule.isDisposed()) return
      val moduleType = ModuleType.get(myModule)
      for (importer in myImporters) {
        try {
          if (importer.moduleType === moduleType) {
            measureImporterTime(importer, counters, true) {
              importer.preProcess(myModule, myMavenProject, myMavenProjectChanges, myModifiableModelsProvider)
            }
          }
        }
        catch (e: Exception) {
          MavenLog.LOG.error("Exception in MavenImporter.preConfig, skipping it.", e)
        }
      }
    }

    fun config(postTasks: List<MavenProjectsProcessorTask>, counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      doConfigurationStep { doConfig(postTasks, counters) }
    }

    private fun doConfig(postTasks: List<MavenProjectsProcessorTask>, counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      if (myModule.isDisposed()) return
      val moduleType = ModuleType.get(myModule)
      for (importer in myImporters) {
        if (importer.moduleType === moduleType) {
          try {
            measureImporterTime(importer, counters, false) {
              importer.process(
                myModifiableModelsProvider!!,
                myModule,
                myRootModelAdapter!!,
                myMavenProjectsTree,
                myMavenProject,
                myMavenProjectChanges,
                myMavenProjectToModuleName,
                postTasks)
            }
          }
          catch (e: Exception) {
            MavenLog.LOG.error("Exception in MavenImporter.config, skipping it.", e)
          }
        }
      }
    }

    fun postConfig(counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      doConfigurationStep { doPostConfig(counters) }
    }

    private fun doPostConfig(counters: MutableMap<Class<out MavenImporter>, CountAndTime>) {
      if (myModule.isDisposed()) return
      val moduleType = ModuleType.get(myModule)
      for (importer in myImporters) {
        try {
          if (importer.moduleType == moduleType) {
            measureImporterTime(importer, counters, false) {
              importer.postProcess(myModule, myMavenProject, myMavenProjectChanges, myModifiableModelsProvider)
            }
          }
        }
        catch (e: Exception) {
          MavenLog.LOG.error("Exception in MavenImporter.postConfig, skipping it.", e)
        }
      }
    }

    class CountAndTime {
      var count = 0
      var timeNano: Long = 0
    }

    companion object {
      @JvmStatic
      fun createIfApplicable(mavenProject: MavenProject,
                             module: Module,
                             moduleType: StandardMavenModuleType,
                             mavenTree: MavenProjectsTree,
                             hasChanges: Boolean,
                             mavenProjectToModuleName: Map<MavenProject, String>,
                             mavenImporters: List<MavenImporter>): LegacyExtensionImporter? {
        if (moduleType === StandardMavenModuleType.COMPOUND_MODULE) return null
        var suitableImporters = mavenImporters
        val changes = if (hasChanges) MavenProjectChanges.ALL else MavenProjectChanges.NONE
        return if (suitableImporters.isEmpty()) null
        else LegacyExtensionImporter(module, mavenTree, mavenProject, changes, mavenProjectToModuleName, suitableImporters)
      }

      private fun measureImporterTime(importer: MavenImporter,
                                      counters: MutableMap<Class<out MavenImporter>, CountAndTime>,
                                      increaseModuleCounter: Boolean,
                                      r: Runnable) {
        val before = System.nanoTime()
        try {
          r.run()
        }
        finally {
          val countAndTime = counters.computeIfAbsent(importer.javaClass) { _: Class<out MavenImporter>? -> CountAndTime() }
          if (increaseModuleCounter) countAndTime.count++
          countAndTime.timeNano += System.nanoTime() - before
        }
      }
    }
  }
}
