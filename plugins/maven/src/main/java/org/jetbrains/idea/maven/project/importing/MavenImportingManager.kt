// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEventsNls
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil

@ApiStatus.Experimental
class MavenImportingManager(val project: Project) {
  var currentContext: MavenImportContext? = null
    private set

  private val console by lazy {
    project.getService(MavenProjectsManager::class.java).syncConsole
  }

  private val waitingPromises = ArrayList<AsyncPromise<MavenImportFinishedContext>>()

  fun linkAndImportFile(pom: VirtualFile): Promise<MavenImportFinishedContext> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val manager = MavenProjectsManager.getInstance(project);
    val importPath = if(pom.isDirectory) RootPath(pom) else FilesList(pom)
    return openProjectAndImport(importPath, manager.importingSettings, manager.generalSettings);
  }

  fun openProjectAndImport(importPaths: ImportPaths,
                           importingSettings: MavenImportingSettings,
                           generalSettings: MavenGeneralSettings): Promise<MavenImportFinishedContext> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    assertNoCurrentImport()
    MavenUtil.setupProjectSdk(project)
    currentContext = MavenStartedImport(project)
    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, MavenProjectBundle.message("maven.project.importing")) {
        override fun run(indicator: ProgressIndicator) {
          try {
            val finishedContext = doImport(
              MavenProgressIndicator(project, indicator, null),
              importPaths,
              generalSettings,
              importingSettings
            )
            val promises = getAndClearWaitingPromises(finishedContext)
            promises.forEach { it.setResult(finishedContext) }
          }
          catch (e: Throwable) {
            val promises = getAndClearWaitingPromises(MavenImportFinishedContext(e, project))
            if (indicator.isCanceled) {
              promises.forEach { it.setError("Cancelled") }
            }
            else {
              MavenLog.LOG.error(e)
              promises.forEach { it.setError(e) }
            }
          }
        }
      })

    }
    return getImportFinishPromise()
  }

  private fun assertNoCurrentImport() {
    if (currentContext != null) {
      if (currentContext !is MavenImportFinishedContext) {
        throw IllegalStateException("Importing is in progress already")
      }
    }
  }

  private fun doImport(indicator: MavenProgressIndicator,
                       importPaths: ImportPaths,
                       generalSettings: MavenGeneralSettings,
                       importingSettings: MavenImportingSettings): MavenImportFinishedContext {

    val flow = MavenImportFlow()

    return runSync {
      @Suppress("HardCodedStringLiteral")
      console.addWarning("New Maven importing flow is enabled", "New Maven importing flow is enabled, it is experimental feature. " +
                                                                "\n\n" +
                                                                "To revert to old importing flow, set \"maven.new.import\" registry flag to false");
      val initialImport = flow.prepareNewImport(project, indicator, importPaths, generalSettings, importingSettings, emptyList(),
                                                emptyList())
      currentContext = initialImport

      val readMavenFiles = doTask(MavenProjectBundle.message("maven.reading")) {
        currentContext?.indicator?.checkCanceled()
        flow.readMavenFiles(initialImport)
      }

      val dependenciesContext = doTask(MavenProjectBundle.message("maven.resolving")) {
        currentContext?.indicator?.checkCanceled()
        flow.resolveDependencies(readMavenFiles)
      }
      val resolvePlugins = doTask(MavenProjectBundle.message("maven.downloading.plugins")) {
        currentContext?.indicator?.checkCanceled()
        flow.resolvePlugins(dependenciesContext)
      }

      val foldersResolved = doTask(MavenProjectBundle.message("maven.updating.folders")) {
        currentContext?.indicator?.checkCanceled()
        flow.resolveFolders(dependenciesContext)
      }

      val importContext = doTask(MavenProjectBundle.message("maven.project.importing")) {
        currentContext?.indicator?.checkCanceled()
        flow.commitToWorkspaceModel(dependenciesContext)
      }

      return@runSync doTask(MavenProjectBundle.message("maven.post.processing")) {
        currentContext?.indicator?.checkCanceled()
        flow.runPostImportTasks(importContext)
        flow.updateProjectManager(readMavenFiles)
        flow.configureMavenProject(importContext)
        return@doTask MavenImportFinishedContext(importContext)
      }
    }.also { it.context?.let(flow::runImportExtensions) }

  }

  private fun getAndClearWaitingPromises(finishedContext: MavenImportFinishedContext): List<AsyncPromise<MavenImportFinishedContext>> {
    val ref = Ref<ArrayList<AsyncPromise<MavenImportFinishedContext>>>()
    ApplicationManager.getApplication().invokeAndWait {
      val result = ArrayList(waitingPromises)
      ref.set(result)
      waitingPromises.clear()
      currentContext = finishedContext
    }
    return ref.get()
  }


  fun isImportingInProgress(): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return currentContext != null && currentContext !is MavenImportFinishedContext
  }

  fun getImportFinishPromise(): Promise<MavenImportFinishedContext> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val context = currentContext
    if (context is MavenImportFinishedContext) return resolvedPromise(context)
    val result = AsyncPromise<MavenImportFinishedContext>()
    waitingPromises.add(result)
    return result
  }

  fun sheduleImportAll(): Promise<MavenImportFinishedContext> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val manager = MavenProjectsManager.getInstance(project)
    val settings = MavenWorkspaceSettingsComponent.getInstance(project)
    return openProjectAndImport(FilesList(manager.collectAllAvailablePomFiles()),
                                settings.settings.getImportingSettings(),
                                settings.settings.getGeneralSettings())
  }


  private fun <Result> doTask(message: @BuildEventsNls.Message String,
                              init: () -> Result): Result where Result : MavenImportContext {
    return console.runTask(message, init).also { currentContext = it }
  }

  private fun runSync(init: () -> MavenImportFinishedContext): MavenImportFinishedContext {
    console.startImport(project.getService(SyncViewManager::class.java))
    try {
      return init()
    }
    catch (e: Exception) {
      console.addException(e, project.getService(SyncViewManager::class.java))
      return MavenImportFinishedContext(e, project)
    }
    finally {
      console.finishImport()
    }
  }

  fun forceStopImport() {
    currentContext?.let { it.indicator.cancel() }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenImportingManager {
      return project.getService(MavenImportingManager::class.java)
    }
  }
}