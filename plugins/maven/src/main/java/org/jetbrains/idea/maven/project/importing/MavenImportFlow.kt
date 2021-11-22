// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.ide.starters.shared.MAVEN_PROJECT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.exists
import org.jetbrains.idea.maven.execution.BTWMavenConsole
import org.jetbrains.idea.maven.importing.MavenProjectImporter
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.IOException
import java.nio.file.Path

class MavenImportFlow {


  fun prepareNewImport(project: Project,
                       indicator: MavenProgressIndicator,
                       pomFiles: List<VirtualFile>,
                       generalSettings: MavenGeneralSettings,
                       importingSettings: MavenImportingSettings,
                       enabledProfiles: Collection<String>,
                       disabledProfiles: Collection<String>): MavenInitialImportContext {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(project)
    //assert(!projectManager.isMavenizedProject)
    val isVeryNewProject = project.getUserData<Boolean>(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT)
    if (isVeryNewProject == true) {
      ExternalStorageConfigurationManager.getInstance(project).isEnabled = true
    }
    MavenUtil.setupProjectSdk(project)

    val profiles = MavenExplicitProfiles(enabledProfiles, disabledProfiles)
    return MavenInitialImportContext(project, pomFiles, profiles, generalSettings, importingSettings, indicator)
  }

  fun prepareReimport(project: Project, indicator: MavenProgressIndicator,
                      enabledProfiles: Collection<String>,
                      disabledProfiles: Collection<String>): MavenInitialImportContext {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(project)
    assert(projectManager.isMavenizedProject)
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val pomFiles = projectManager.collectAllAvailablePomFiles()
    projectManager.removeIgnoredFilesPaths(MavenUtil.collectPaths(pomFiles))

    val profiles = MavenExplicitProfiles(enabledProfiles, disabledProfiles)
    return MavenInitialImportContext(project, pomFiles, profiles,
                                     MavenWorkspaceSettingsComponent.getInstance(project).settings.getGeneralSettings(),
                                     MavenWorkspaceSettingsComponent.getInstance(project).settings.getImportingSettings(), indicator)
  }

  fun readMavenFiles(context: MavenInitialImportContext): MavenReadContext {
    val projectManager = MavenProjectsManager.getInstance(context.project)
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val projectsTree = loadOrCreateProjectTree(projectManager)
    MavenProjectsManager.applyStateToTree(projectsTree, projectManager)
    projectsTree.addManagedFilesWithProfiles(context.pomFiles, context.profiles)
    val toResolve = HashSet<MavenProject>()
    val errorsSet = HashSet<MavenProject>()
    val d = Disposer.newDisposable("MavenImportFlow:readMavenFiles:treeListener")
    Disposer.register(projectManager, d)
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

      }
    }, d)

    projectsTree.update(context.pomFiles, true, context.generalSettings, context.indicator)
    Disposer.dispose(d)
    return MavenReadContext(context.project, projectsTree, toResolve, errorsSet, context)
  }

  private fun loadOrCreateProjectTree(projectManager: MavenProjectsManager): MavenProjectsTree {
    val file = projectManager.projectsTreeFile
    try {
      if (file.exists()) {
        return MavenProjectsTree.read(projectManager.project, file) ?: MavenProjectsTree(projectManager.project)
      }
    }
    catch (e: IOException) {
      MavenLog.LOG.info(e)
    }

    return MavenProjectsTree(projectManager.project)
  }

  fun resolveDependencies(context: MavenReadContext): MavenResolvedContext {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val resolver = MavenProjectResolver(context.projectsTree)
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.initialContext.generalSettings.outputLevel,
                                             context.initialContext.generalSettings.isPrintErrorStackTraces)
    val resolveContext = ResolveContext()
    val d = Disposer.newDisposable("MavenImportFlow:resolveDependencies:treeListener")
    Disposer.register(projectManager, d)
    val projectsToImport = ArrayList<MavenProject>()
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
    }, d);
    resolver.resolve(context.project, context.toResolve, context.initialContext.generalSettings, embeddersManager, consoleToBeRemoved,
                     resolveContext, context.initialContext.indicator)
    Disposer.dispose(d)
    return MavenResolvedContext(context.project, resolveContext.getUserData(MavenProjectResolver.UNRESOLVED_ARTIFACTS) ?: emptySet(),
                                projectsToImport, nativeProjectStorage, context)
  }

  fun resolvePlugins(context: MavenResolvedContext): MavenPluginResolvedContext {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val resolver = MavenProjectResolver(context.readContext.projectsTree)
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.initialContext.generalSettings.outputLevel,
                                             context.initialContext.generalSettings.isPrintErrorStackTraces)

    val unresolvedPlugins = HashMap<MavenPlugin, Path?>()
    context.nativeProjectHolder.forEach {
      unresolvedPlugins.putAll(resolver.resolvePlugins(context.project, it.first, it.second, embeddersManager, consoleToBeRemoved,
                                                       context.initialContext.indicator, false))
    }
    return MavenPluginResolvedContext(context.project, unresolvedPlugins, context)
  }

  fun downloadArtifacts(context: MavenResolvedContext, sources: Boolean, javadocs: Boolean): MavenArtifactDownloader.DownloadResult {
    if (!(sources || javadocs)) return MavenArtifactDownloader.DownloadResult()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val resolver = MavenProjectResolver(context.readContext.projectsTree)
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.initialContext.generalSettings.outputLevel,
                                             context.initialContext.generalSettings.isPrintErrorStackTraces)
    return resolver.downloadSourcesAndJavadocs(context.project, context.projectsToImport, null, sources, javadocs,
                                               embeddersManager,
                                               consoleToBeRemoved, context.initialContext.indicator)

  }

  fun resolveFolders(context: MavenResolvedContext): MavenResolvedContext {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val resolver = MavenProjectResolver(context.readContext.projectsTree)
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.initialContext.generalSettings.outputLevel,
                                             context.initialContext.generalSettings.isPrintErrorStackTraces)
    val d = Disposer.newDisposable("MavenImportFlow:resolveFolders:treeListener")
    val projectsToImport = HashSet<MavenProject>(context.projectsToImport);
    Disposer.register(projectManager, d)
    context.readContext.projectsTree.addListener(object : MavenProjectsTree.Listener {
      override fun foldersResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
        if (shouldScheduleProject(projectWithChanges.first, projectWithChanges.second)) {
          projectsToImport.add(projectWithChanges.first)
        }
      }
    }, d)
    context.projectsToImport.forEach {
      resolver.resolveFolders(it, context.initialContext.importingSettings,
                              embeddersManager,
                              consoleToBeRemoved, context.initialContext.indicator)
    }


    Disposer.dispose(d)
    return MavenResolvedContext(context.project, context.unresolvedArtifacts, projectsToImport.toList(), context.nativeProjectHolder,
                                context.readContext);
  }

  fun commitToWorkspaceModel(context: MavenResolvedContext): MavenImportedContext {
    val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(context.project)
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val projectImporter = MavenProjectImporter(context.project, context.readContext.projectsTree,
                                               projectManager.getFileToModuleMapping(MavenDefaultModelsProvider(context.project)),
                                               context.projectsToImport.map {
                                                 it to MavenProjectChanges.ALL
                                               }.toMap(), false, modelsProvider, context.initialContext.importingSettings, null)
    val postImportTasks = projectImporter.importProject();
    val modulesCreated = projectImporter.createdModules
    projectManager.setProjectsTree(context.readContext.projectsTree)
    return MavenImportedContext(context.project, modulesCreated, postImportTasks, context.initialContext);
  }

  fun runImportExtensions(context: MavenImportedContext): MavenImportingExtensionsContext {
    TODO()
  }

  fun runPostImportTasks(context: MavenImportedContext) {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val projectManager = MavenProjectsManager.getInstance(context.project)
    val embeddersManager = projectManager.embeddersManager
    val consoleToBeRemoved = BTWMavenConsole(context.project, context.initialContext.generalSettings.outputLevel,
                                             context.initialContext.generalSettings.isPrintErrorStackTraces)
    context.postImportTasks?.forEach {
      it.perform(context.project, embeddersManager, consoleToBeRemoved, context.initialContext.indicator)
    }
  }

  private fun shouldScheduleProject(project: MavenProject, changes: MavenProjectChanges): Boolean {
    return !project.hasReadingProblems() && changes.hasChanges()
  }

}