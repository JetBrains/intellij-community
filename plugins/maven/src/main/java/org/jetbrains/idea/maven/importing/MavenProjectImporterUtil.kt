// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.launch
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenProjectImporterUtil.LegacyExtensionImporter.CountAndTime
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.statistics.MavenImportCollector
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@ApiStatus.Internal
object MavenProjectImporterUtil {
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

  fun scheduleRefreshResolvedArtifacts(postTasks: MutableList<MavenProjectsProcessorTask>,
                                       projectsToRefresh: Iterable<MavenProject>) {
    if (!Registry.`is`("maven.sync.refresh.resolved.artifacts", false)) return

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

  private class RefreshingFilesTask(private val myFiles: Set<File>) : MavenProjectsProcessorTask {
    override fun perform(project: Project,
                         embeddersManager: MavenEmbeddersManager,
                         indicator: ProgressIndicator) {
      val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
      cs.launch {
        doRefreshFiles(myFiles)
      }
    }
  }

  @ApiStatus.Internal
  fun removeOutdatedCompilerConfigSettings(project: Project) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val javacOptions = JavacConfiguration.getOptions(project, JavacConfiguration::class.java)
    var options = javacOptions.ADDITIONAL_OPTIONS_STRING
    options = options.replaceFirst("(-target (\\S+))".toRegex(), "") // Old IDEAs saved
    javacOptions.ADDITIONAL_OPTIONS_STRING = options
  }

  private fun doRefreshFiles(files: Set<File>) {
    LocalFileSystem.getInstance().refreshIoFiles(files)
  }

  @JvmStatic
  fun selectScope(mavenScope: String?): DependencyScope {
    if (MavenConstants.SCOPE_RUNTIME == mavenScope) return DependencyScope.RUNTIME
    if (MavenConstants.SCOPE_TEST == mavenScope) return DependencyScope.TEST
    return if (MavenConstants.SCOPE_PROVIDED == mavenScope) DependencyScope.PROVIDED else DependencyScope.COMPILE
  }

  fun getAttachedJarsLibName(artifact: MavenArtifact): String {
    var libraryName = artifact.getLibraryName()
    assert(libraryName.startsWith(MavenArtifact.MAVEN_LIB_PREFIX))
    libraryName = MavenArtifact.MAVEN_LIB_PREFIX + "ATTACHED-JAR: " + libraryName.substring(MavenArtifact.MAVEN_LIB_PREFIX.length)
    return libraryName
  }

  fun createCopyForLocalRepo(artifact: MavenArtifact, project: MavenProject): MavenArtifact {
    return MavenArtifact(
      artifact.groupId,
      artifact.artifactId,
      artifact.version,
      artifact.baseVersion,
      artifact.type,
      artifact.classifier,
      artifact.scope,
      artifact.isOptional,
      artifact.extension,
      null,
      project.localRepositoryPath.toFile(),
      false, false
    )
  }

  val IMPORTED_CLASSIFIERS = setOf("client")

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
                             changes: MavenProjectChanges,
                             mavenProjectToModuleName: Map<MavenProject, String>,
                             mavenImporters: List<MavenImporter>): LegacyExtensionImporter? {
        if (moduleType === StandardMavenModuleType.COMPOUND_MODULE) return null
        var suitableImporters = mavenImporters
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

  private const val COMPILER_PLUGIN_GROUP_ID = "org.apache.maven.plugins"

  private const val COMPILER_PLUGIN_ARTIFACT_ID = "maven-compiler-plugin"

  fun MavenProject.getAllCompilerConfigs(): List<Element> {
    val result = ArrayList<Element>(1)
    this.getPluginConfiguration(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID)?.let(result::add)

    this.findPlugin(COMPILER_PLUGIN_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID)
      ?.executions?.filter { it.goals.contains("compile") }
      ?.filter { it.phase != "none" }
      ?.mapNotNull { it.configurationElement }
      ?.forEach(result::add)
    return result
  }
}
