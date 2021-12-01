// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.build.SyncViewManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
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

  private val waitingPromises = ArrayList<AsyncPromise<MavenImportFinishedContext>>()

  fun openProjectAndImport(importPaths: ImportPaths,
                           importingSettings: MavenImportingSettings,
                           generalSettings: MavenGeneralSettings): Promise<MavenImportFinishedContext> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (currentContext != null) {
      if (currentContext !is MavenImportFinishedContext) {
        throw IllegalStateException("Importing is in progress already")
      }
    }
    MavenUtil.setupProjectSdk(project)
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
            val promises = getAndClearWaitingPromises()
            promises.forEach { it.setResult(finishedContext) }
          }
          catch (e: Throwable) {
            MavenLog.LOG.error(e)
            val promises = getAndClearWaitingPromises()
            promises.forEach { it.setError(e) }
          }
        }
      })

    }
    return getImportFinishPromise()
  }

  private fun doImport(indicator: MavenProgressIndicator,
                       importPaths: ImportPaths,
                       generalSettings: MavenGeneralSettings,
                       importingSettings: MavenImportingSettings): MavenImportFinishedContext {

    val mavenImportStatusConsole = MavenImportStatusConsole(project)
    val flow = MavenImportFlow()
    mavenImportStatusConsole.start()
    val initialImport = flow.prepareNewImport(project, indicator, importPaths, generalSettings, importingSettings, emptyList(), emptyList())
    currentContext = initialImport

    val readMavenFiles = flow.readMavenFiles(initialImport)
    currentContext = readMavenFiles
    val dependenciesContext = flow.resolveDependencies(readMavenFiles)
    currentContext = dependenciesContext
    val resolvePlugins = flow.resolvePlugins(dependenciesContext)
    currentContext = resolvePlugins
    val foldersResolved = flow.resolveFolders(dependenciesContext)
    currentContext = foldersResolved
    val importContext = flow.commitToWorkspaceModel(dependenciesContext)
    currentContext = importContext
    flow.runPostImportTasks(importContext)
    val finishedContext = MavenImportFinishedContext(importContext)
    currentContext = finishedContext
    flow.updateProjectManager(readMavenFiles)
    mavenImportStatusConsole.finish()
    flow.runImportExtensions(importContext)
    return finishedContext
  }

  private fun getAndClearWaitingPromises(): List<AsyncPromise<MavenImportFinishedContext>> {
    val ref = Ref<ArrayList<AsyncPromise<MavenImportFinishedContext>>>()
    ApplicationManager.getApplication().invokeAndWait {
      val result = ArrayList(waitingPromises)
      ref.set(result)
      waitingPromises.clear()
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


  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenImportingManager {
      return project.getService(MavenImportingManager::class.java)
    }
  }
}