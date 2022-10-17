// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.MavenDisposable
import org.jetbrains.idea.maven.execution.BTWMavenConsole
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.project.actions.LookForNestedToggleAction
import org.jetbrains.idea.maven.server.MavenWrapperDownloader
import org.jetbrains.idea.maven.server.MavenWrapperSupport
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.FileFinder
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.*

@IntellijInternalApi
@ApiStatus.Internal
@ApiStatus.Experimental
class MavenImportFlow {
  fun prepareNewImport(project: Project,
                       importPaths: ImportPaths,
                       generalSettings: MavenGeneralSettings,
                       importingSettings: MavenImportingSettings,
                       enabledProfiles: Collection<String>,
                       disabledProfiles: Collection<String>): MavenInitialImportContext {
    val isVeryNewProject = project.getUserData<Boolean>(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == true
                           && ModuleManager.getInstance(project).modules.size == 0
    if (isVeryNewProject) {
      ExternalStorageConfigurationManager.getInstance(project).isEnabled = true
    }
    val previewModule = if (isVeryNewProject) createPreviewModule(importPaths, project) else null

    val manager = MavenProjectsManager.getInstance(project)
    val profiles = MavenExplicitProfiles(enabledProfiles, disabledProfiles)
    val ignorePaths = manager.ignoredFilesPaths
    val ignorePatterns = manager.ignoredFilesPatterns

    val importDisposable = Disposer.newDisposable("MavenImportFlow:importDisposable" + System.currentTimeMillis())
    Disposer.register(MavenDisposable.getInstance(project), importDisposable)

    return MavenInitialImportContext(project, importPaths, profiles, generalSettings, importingSettings, ignorePaths, ignorePatterns,
                                     importDisposable,
                                     previewModule, Exception())
  }

  private fun createPreviewModule(importPaths: ImportPaths, project: Project): Module? {
    if (Registry.`is`("maven.create.dummy.module.on.first.import")) {
      val contentRoot = when (importPaths) {
        is FilesList -> ContainerUtil.getFirstItem(importPaths.poms).parent
        is RootPath -> importPaths.path
      }
      return MavenImportUtil.createPreviewModule(project, contentRoot)
    }
    return null
  }

  fun readMavenFiles(context: MavenInitialImportContext, indicator: MavenProgressIndicator): MavenReadContext {
    val projectManager = MavenProjectsManager.getInstance(context.project)
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val ignorePaths: List<String> = context.ignorePaths
    val ignorePatterns: List<String> = context.ignorePatterns
    val projectsTree = loadOrCreateProjectTree(projectManager)
    MavenProjectsManager.applyStateToTree(projectsTree, projectManager)
    val managedFilesPath = MavenProjectsManager.getInstance(context.project).projectsTree.managedFilesPaths

    val pomFiles = LinkedHashSet<VirtualFile>()
    managedFilesPath.mapNotNull { LocalFileSystem.getInstance().findFileByPath(it) }.also {
      pomFiles.addAll(it)
    }

    val newPomFiles = when (context.paths) {
      is FilesList -> context.paths.poms
      is RootPath -> searchForMavenFiles(context.paths.path, context.indicator)
    }
    pomFiles.addAll(newPomFiles)

    projectsTree.addManagedFilesWithProfiles(pomFiles.filter { it.exists() }.toList(), context.profiles)
    val toResolve = LinkedHashSet<MavenProject>()
    val errorsSet = LinkedHashSet<MavenProject>()
    val d = Disposer.newDisposable("MavenImportFlow:readMavenFiles:treeListener")
    Disposer.register(context.importDisposable, d)
    projectsTree.addListener(projectManager.treeListenerEventDispatcher.multicaster, context.importDisposable)
    projectsTree.addListener(object : MavenProjectsTree.Listener {
      override fun projectsUpdated(updated: MutableList<Pair<MavenProject, MavenProjectChanges>>, deleted: MutableList<MavenProject>) {
        val allUpdated = MavenUtil.collectFirsts(
          updated) // import only updated projects and dependents of them (we need to update faced-deps, packaging etc);

        toResolve.addAll(allUpdated)

        for (eachDependent in projectsTree.getDependentProjects(allUpdated)) {
          toResolve.add(eachDependent)
        }


        // resolve updated, theirs dependents, and dependents of deleted
        toResolve.addAll(projectsTree.getDependentProjects(ContainerUtil.concat(allUpdated, deleted)))

        errorsSet.addAll(toResolve.filter { it.hasReadingProblems() })
        toResolve.removeIf { it.hasReadingProblems() }

        runLegacyListeners(context) { projectsScheduled() }
      }
    }, d)

    if (ignorePaths.isNotEmpty()) {
      projectsTree.ignoredFilesPaths = ignorePaths
    }
    if (ignorePatterns.isNotEmpty()) {
      projectsTree.ignoredFilesPatterns = ignorePatterns
    }

    projectsTree.updateAll(true, context.generalSettings, indicator)
    Disposer.dispose(d)
    val workingDir = getWorkingBaseDir(context)
    val wrapperData = MavenWrapperSupport.getWrapperDistributionUrl(workingDir)?.let { WrapperData(it, workingDir!!) }
    readDoubleUpdateToWorkaroundIssueWhenProjectToBeReadTwice(context, projectsTree, indicator)
    return MavenReadContext(context.project, projectsTree, toResolve, errorsSet, context, wrapperData, indicator)
  }

  //TODO: Remove this. See StructureImportingTest.testProjectWithMavenConfigCustomUserSettingsXml
  private fun readDoubleUpdateToWorkaroundIssueWhenProjectToBeReadTwice(context: MavenInitialImportContext,
                                                                        projectsTree: MavenProjectsTree,
                                                                        indicator: MavenProgressIndicator) {
    context.generalSettings.updateFromMavenConfig(projectsTree.rootProjectsFiles)
    projectsTree.updateAll(true, context.generalSettings, indicator)
  }

  fun setupMavenWrapper(readContext: MavenReadContext): MavenReadContext {
    if (readContext.wrapperData == null) return readContext
    if (!MavenUtil.isWrapper(readContext.initialContext.generalSettings)) return readContext
    MavenWrapperDownloader.checkOrInstallForSync(readContext.project, readContext.wrapperData.baseDir.path)
    return readContext
  }


  private fun getWorkingBaseDir(context: MavenInitialImportContext): VirtualFile? {
    val guessedDir = context.project.guessProjectDir()
    if (guessedDir != null) return guessedDir
    when (context.paths) {
      is FilesList -> return context.paths.poms[0].parent
      is RootPath -> return context.paths.path
    }
  }

  private fun searchForMavenFiles(path: VirtualFile, indicator: MavenProgressIndicator): MutableList<VirtualFile> {
    indicator.setText(MavenProjectBundle.message("maven.locating.files"))
    return FileFinder.findPomFiles(path.children, LookForNestedToggleAction.isSelected(), indicator)
  }

  private fun loadOrCreateProjectTree(projectManager: MavenProjectsManager): MavenProjectsTree {
    return projectManager.projectsTree.copyForReimport
  }

  fun resolveDependencies(context: MavenReadContext): MavenResolvedContext {
    runLegacyListeners(context) { importAndResolveScheduled() }
    assertNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val resolver = MavenProjectResolver(context.projectsTree)
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.initialContext.generalSettings.outputLevel,
                                             context.initialContext.generalSettings.isPrintErrorStackTraces)
    val resolveContext = ResolveContext(context.projectsTree)
    val d = Disposer.newDisposable("MavenImportFlow:resolveDependencies:treeListener")
    Disposer.register(context.initialContext.importDisposable, d)
    val projectsToImport = ArrayList(context.toResolve)
    val nativeProjectStorage = ArrayList<kotlin.Pair<MavenProject, NativeMavenProjectHolder>>()
    context.projectsTree.addListener(object : MavenProjectsTree.Listener {
      override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>,
                                   nativeMavenProject: NativeMavenProjectHolder?) {
        if (nativeMavenProject != null) {
          if (shouldScheduleProject(projectWithChanges.first, projectWithChanges.second)) {
            projectsToImport.add(projectWithChanges.first)
          }
          nativeProjectStorage.add(projectWithChanges.first to nativeMavenProject)
        }
      }
    }, d)
    resolver.resolve(context.project, context.toResolve, context.initialContext.generalSettings, embeddersManager, consoleToBeRemoved,
                     resolveContext, context.initialContext.indicator)
    Disposer.dispose(d)
    return MavenResolvedContext(context.project, resolveContext.getUserData(MavenProjectResolver.UNRESOLVED_ARTIFACTS) ?: emptySet(),
                                projectsToImport, nativeProjectStorage, context)
  }

  fun resolvePlugins(context: MavenResolvedContext): MavenPluginResolvedContext {
    assertNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val resolver = MavenProjectResolver(context.readContext.projectsTree)
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.initialContext.generalSettings.outputLevel,
                                             context.initialContext.generalSettings.isPrintErrorStackTraces)

    val unresolvedPlugins = Collections.synchronizedSet(LinkedHashSet<MavenPlugin>())

    context.nativeProjectHolder.foreachParallel {
      unresolvedPlugins.addAll(
        resolver.resolvePlugins(it.first, it.second, embeddersManager, consoleToBeRemoved, context.initialContext.indicator, false,
                                projectManager.forceUpdateSnapshots))
    }
    return MavenPluginResolvedContext(context.project, unresolvedPlugins, context)
  }

  fun downloadArtifacts(context: MavenResolvedContext, sources: Boolean, javadocs: Boolean): MavenArtifactDownloader.DownloadResult {
    assertNonDispatchThread()
    if (!(sources || javadocs)) return MavenArtifactDownloader.DownloadResult()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val resolver = MavenProjectResolver(context.readContext.projectsTree)
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.initialContext.generalSettings.outputLevel,
                                             context.initialContext.generalSettings.isPrintErrorStackTraces)
    return resolver.downloadSourcesAndJavadocs(context.project, context.projectsToImport, null, sources, javadocs, embeddersManager,
                                               consoleToBeRemoved, context.initialContext.indicator)

  }

  fun downloadSpecificArtifacts(project: Project,
                                mavenProjects: Collection<MavenProject>,
                                mavenArtifacts: Collection<MavenArtifact>?,
                                sources: Boolean,
                                javadocs: Boolean,
                                indicator: MavenProgressIndicator): MavenArtifactDownloader.DownloadResult {
    assertNonDispatchThread()
    if (!(sources || javadocs)) return MavenArtifactDownloader.DownloadResult()
    val projectManager = MavenProjectsManager.getInstance(project)
    val embeddersManager = projectManager.embeddersManager
    val resolver = MavenProjectResolver(projectManager.projectsTree)
    val settings = MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings()
    val consoleToBeRemoved = BTWMavenConsole(project, settings.outputLevel, settings.isPrintErrorStackTraces)
    return resolver.downloadSourcesAndJavadocs(project, mavenProjects, mavenArtifacts, sources, javadocs, embeddersManager,
                                               consoleToBeRemoved, indicator)

  }

  fun resolveFolders(projects: Collection<MavenProject>, project: Project, indicator: MavenProgressIndicator): Collection<MavenProject> {
    assertNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(project)
    val embeddersManager = projectManager.embeddersManager
    val projectTree = loadOrCreateProjectTree(projectManager)
    val resolver = MavenProjectResolver(loadOrCreateProjectTree(projectManager))
    val generalSettings = MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings()
    val importingSettings = MavenWorkspaceSettingsComponent.getInstance(project).settings.getImportingSettings()
    val consoleToBeRemoved = BTWMavenConsole(project, generalSettings.outputLevel,
                                             generalSettings.isPrintErrorStackTraces)
    val d = Disposer.newDisposable("MavenImportFlow:resolveFolders:treeListener")
    val projectsFoldersResolved = Collections.synchronizedList(ArrayList<MavenProject>())
    Disposer.register(MavenDisposable.getInstance(project), d)
    projectTree.addListener(object : MavenProjectsTree.Listener {
      override fun foldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
        if (projectWithChanges.second.hasChanges()) {
          projectsFoldersResolved.add(projectWithChanges.first)
        }
      }
    }, d)
    projects.foreachParallel {
      resolver.resolveFolders(it, importingSettings, embeddersManager, consoleToBeRemoved, indicator)
    }

    Disposer.dispose(d)
    return projectsFoldersResolved
  }

  fun commitToWorkspaceModel(context: MavenResolvedContext, importingActivity: StructuredIdeActivity): MavenImportedContext {
    val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(context.project)
    assertNonDispatchThread()
    val projectImporter = MavenProjectImporter.createImporter(context.project, context.readContext.projectsTree,
                                                              context.projectsToImport.map {
                                                                it to MavenProjectChanges.ALL
                                                              }.toMap(), context.initialContext.importingSettings.isCreateModuleGroups,
                                                              modelsProvider, context.initialContext.importingSettings,
                                                              context.initialContext.previewModule, importingActivity)
    val postImportTasks = projectImporter.importProject()
    val modulesCreated = projectImporter.createdModules()
    return MavenImportedContext(context.project, modulesCreated, postImportTasks, context.readContext, context)
  }

  fun updateProjectManager(context: MavenReadContext) {
    val projectManager = MavenProjectsManager.getInstance(context.project)
    projectManager.projectsTree = context.projectsTree

  }

  fun runPostImportTasks(context: MavenImportedContext) {
    assertNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.readContext.initialContext.generalSettings.outputLevel,
                                             context.readContext.initialContext.generalSettings.isPrintErrorStackTraces)
    context.postImportTasks?.forEach {
      it.perform(context.project, embeddersManager, consoleToBeRemoved, context.readContext.indicator)
    }
  }


  private fun shouldScheduleProject(project: MavenProject, changes: MavenProjectChanges): Boolean {
    return !project.hasReadingProblems() && changes.hasChanges()
  }

  private fun <A> Collection<A>.foreachParallel(f: suspend (A) -> Unit) {
    runBlocking {
      forEach { launch { f(it) } }
    }
  }
}

internal fun assertNonDispatchThread() {
  val app = ApplicationManager.getApplication()
  if (app.isUnitTestMode && app.isDispatchThread) {
    throw RuntimeException("Access from event dispatch thread is not allowed")
  }
  ApplicationManager.getApplication().assertIsNonDispatchThread()
}

internal fun runLegacyListeners(context: MavenImportContext, method: MavenProjectsManager.Listener.() -> Unit) {
  try {
    method(context.project.messageBus.syncPublisher(MavenImportingManager.LEGACY_PROJECT_MANAGER_LISTENER))
  }
  catch (ignore: Exception) {
  }

}
