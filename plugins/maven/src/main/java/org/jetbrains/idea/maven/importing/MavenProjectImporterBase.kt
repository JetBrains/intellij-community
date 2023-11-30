// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter.ExtensionImporter
import org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter.ExtensionImporter.CountAndTime
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.statistics.MavenImportCollector
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@ApiStatus.Internal
abstract class MavenProjectImporterBase(@JvmField protected val myProject: Project,
                                        @JvmField protected val myProjectsTree: MavenProjectsTree,
                                        @JvmField protected val myImportingSettings: MavenImportingSettings,
                                        @JvmField protected val myIdeModifiableModelsProvider: IdeModifiableModelsProvider) : MavenProjectImporter {
  @JvmField
  protected val myModelsProvider: IdeModifiableModelsProvider = myIdeModifiableModelsProvider

  protected fun selectProjectsToImport(originalProjects: Collection<MavenProject>): Set<MavenProject> {
    val result: MutableSet<MavenProject> = HashSet()
    for (each in originalProjects) {
      if (!shouldCreateModuleFor(each)) continue
      result.add(each)
    }
    return result
  }

  protected fun shouldCreateModuleFor(project: MavenProject): Boolean {
    return if (myProjectsTree.isIgnored(project)) false else !project.isAggregator || myImportingSettings.isCreateModulesForAggregators
  }

  protected class RefreshingFilesTask(private val myFiles: Set<File>) : MavenProjectsProcessorTask {
    override fun perform(project: Project,
                         embeddersManager: MavenEmbeddersManager,
                         indicator: ProgressIndicator) {
      runImportActivitySync(project, MavenUtil.SYSTEM_ID, RefreshingFilesTask::class.java) {
        doPerform(indicator)
      }
    }

    private fun doPerform(indicator: ProgressIndicator) {
      indicator.setText(MavenProjectBundle.message("progress.text.refreshing.files"))
      doRefreshFiles(myFiles)
    }
  }

  companion object {
    @JvmStatic
    fun importExtensions(project: Project,
                         modifiableModelsProvider: IdeModifiableModelsProvider,
                         extensionImporters: List<ExtensionImporter>,
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
        importers.forEach(Consumer { importer: ExtensionImporter -> importer.init(provider) })
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
    fun scheduleRefreshResolvedArtifacts(postTasks: MutableList<MavenProjectsProcessorTask>,
                                         projectsToRefresh: Iterable<MavenProject>) {
      // We have to refresh all the resolved artifacts manually in order to
      // update all the VirtualFilePointers. It is not enough to call
      // VirtualFileManager.refresh() since the newly created files will be only
      // picked by FS when FileWatcher finishes its work. And in the case of import
      // it doesn't finish in time.
      // I couldn't manage to write a test for this since behaviour of VirtualFileManager
      // and FileWatcher differs from real-life execution.
      val files = HashSet<File>()
      for (project in projectsToRefresh) {
        for (dependency in project.dependencies) {
          files.add(dependency.file)
        }
      }
      if (MavenUtil.isMavenUnitTestModeEnabled()) {
        doRefreshFiles(files)
      }
      else {
        postTasks.add(RefreshingFilesTask(files))
      }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun removeOutdatedCompilerConfigSettings(project: Project) {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      val javacOptions = JavacConfiguration.getOptions(project, JavacConfiguration::class.java)
      var options = javacOptions.ADDITIONAL_OPTIONS_STRING
      options = options.replaceFirst("(-target (\\S+))".toRegex(), "") // Old IDEAs saved
      javacOptions.ADDITIONAL_OPTIONS_STRING = options
    }

    protected fun doRefreshFiles(files: Set<File>) {
      LocalFileSystem.getInstance().refreshIoFiles(files)
    }
  }
}
