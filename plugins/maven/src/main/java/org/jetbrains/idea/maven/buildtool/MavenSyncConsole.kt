// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.*
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ExceptionUtil
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenSyncConsole(private val myProject: Project) {
  @Volatile
  private var mySyncView: BuildProgressListener = BuildProgressListener { _, _ -> }
  private var mySyncId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)
  private var finished = false
  private var started = false

  private var myStartedSet = LinkedHashSet<Pair<Any, String>>()

  @Synchronized
  fun startImport(syncView: BuildProgressListener) {
    started = true
    finished = false
    mySyncId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)
    val descriptor = DefaultBuildDescriptor(mySyncId, "Sync", myProject.basePath!!, System.currentTimeMillis())
    mySyncView = syncView
    mySyncView.onEvent(mySyncId, StartBuildEventImpl(descriptor, "Sync ${myProject.name}"))
    debugLog("maven sync: started importing $myProject")
  }

  @Synchronized
  fun addText(text: String) {
    if (!started || finished) return
    addText(mySyncId, text, true)
  }

  @Synchronized
  fun addText(parentId: Any, text: String, stdout: Boolean) {
    if (!started || finished) return
    if (StringUtil.isEmpty(text)) {
      return
    }
    val toPrint = if (text.endsWith('\n')) text else "$text\n"
    mySyncView.onEvent(mySyncId, OutputBuildEventImpl(parentId, toPrint, stdout))
  }

  @Synchronized
  fun finishImport() {
    debugLog("Maven sync: finishImport")
    doFinish(DerivedResultImpl())
  }

  @Synchronized
  fun notifyReadingProblems(file: VirtualFile) {
    debugLog("reading problems in $file")
  }

  fun getListener(type: MavenServerProgressIndicator.ResolveType): ArtifactSyncListener {
    return when (type) {
      MavenServerProgressIndicator.ResolveType.PLUGIN ->ArtifactSyncListenerImpl("maven.sync.plugins")
      MavenServerProgressIndicator.ResolveType.DEPENDENCY ->ArtifactSyncListenerImpl("maven.sync.dependencies")
    }
  }

  @Synchronized
  private fun doFinish(result: EventResult) {
    val tasks = myStartedSet.toList().asReversed()
    debugLog("Tasks $tasks are not completed! Force complete with $result")
    tasks.forEach { completeTask(it.first, it.second, result) }
    mySyncView.onEvent(mySyncId, FinishBuildEventImpl(mySyncId, null, System.currentTimeMillis(), "", result))
    finished = true
    started = false
  }

  @Synchronized
  private fun showError(keyPrefix: String, dependency: String) {
    val umbrellaString = SyncBundle.message("${keyPrefix}.resolve")
    val errorString = SyncBundle.message("${keyPrefix}.resolve.error", dependency)
    startTask(mySyncId, umbrellaString)
    startTask(umbrellaString, errorString)
    addText(umbrellaString, umbrellaString, false)
    completeTask(umbrellaString, errorString, FailureResultImpl())
  }

  @Synchronized
  private fun startTask(parentId: Any, taskName: String) {
    if (!started || finished) return
    debugLog("Maven sync: start $taskName")
    if (myStartedSet.add(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, StartEventImpl(taskName, parentId, System.currentTimeMillis(), taskName))
    }
  }


  @Synchronized
  private fun completeTask(parentId: Any, taskName: String, result: EventResult) {
    if (!started || finished) return
    debugLog("Maven sync: complete $taskName with $result")
    if (myStartedSet.remove(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result))
    }
  }


  private fun debugLog(s: String, exception: Throwable? = null) {
    MavenLog.LOG.debug(s, exception)
  }

  @Synchronized
  private fun completeUmbrellaEvents(keyPrefix: String) {
    val taskName = SyncBundle.message("${keyPrefix}.resolve")
    completeTask(mySyncId, taskName, DerivedResultImpl())
  }

  @Synchronized
  private fun downloadEventStarted(keyPrefix: String, dependency: String) {
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    startTask(mySyncId, downloadString)
    startTask(downloadString, downloadArtifactString)
  }

  @Synchronized
  private fun downloadEventCompleted(keyPrefix: String, dependency: String) {
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    addText(downloadArtifactString, downloadArtifactString, true)
    completeTask(downloadString, downloadArtifactString, SuccessResultImpl(false))
  }

  @Synchronized
  private fun downloadEventFailed(keyPrefix: String, dependency: String, error: String, stackTrace: String?) {
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    if (stackTrace != null) {
      addText(downloadArtifactString, stackTrace, false)
    }
    else {
      addText(downloadArtifactString, error, true)
    }
    completeTask(downloadString, downloadArtifactString, FailureResultImpl(error))
  }


  private inner class ArtifactSyncListenerImpl(val keyPrefix: String) : ArtifactSyncListener {
    override fun downloadStarted(dependency: String) {
      downloadEventStarted(keyPrefix, dependency)
    }

    override fun downloadCompleted(dependency: String) {
      downloadEventCompleted(keyPrefix, dependency)
    }

    override fun downloadFailed(dependency: String, error: String, stackTrace: String?) {
      downloadEventFailed(keyPrefix, dependency, error, stackTrace)
    }

    override fun finish() {
      completeUmbrellaEvents(keyPrefix)
    }

    override fun showError(dependency: String) {
      showError(keyPrefix, dependency)
    }
  }

}

interface ArtifactSyncListener {
  fun showError(dependency: String)
  fun downloadStarted(dependency: String)
  fun downloadCompleted(dependency: String)
  fun downloadFailed(dependency: String, error: String, stackTrace: String?)
  fun finish()
}




