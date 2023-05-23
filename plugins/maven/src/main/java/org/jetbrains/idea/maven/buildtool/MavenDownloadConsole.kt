// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.EventResult
import com.intellij.build.events.impl.*
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.execution.DownloadBundle
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenDownloadConsole(private val myProject: Project) {
  private var started = false
  private var finished = false
  private var hasErrors = false

  private var myTaskId = createTaskId()
  private val shownIssues = HashSet<String>()
  private var myStartedSet = LinkedHashSet<Pair<Any, String>>()

  @Volatile
  private var myProgressListener: BuildProgressListener = BuildProgressListener { _, _ -> }

  @Synchronized
  fun startDownload(progressListener: BuildProgressListener, downloadSources: Boolean, downloadDocs: Boolean) {
    if (started) {
      return
    }
    started = true
    finished = false
    hasErrors = false
    myProgressListener = progressListener
    shownIssues.clear()
    myTaskId = createTaskId()
    val descriptor = DefaultBuildDescriptor(myTaskId, DownloadBundle.message("maven.download.title"), myProject.basePath!!, System.currentTimeMillis())
    descriptor.isActivateToolWindowWhenFailed = false
    descriptor.isActivateToolWindowWhenAdded = false
    var message = ""
    message = if (downloadSources) {
      if (downloadDocs) {
        DownloadBundle.message("maven.download.sources.and.docs")
      } else {
        DownloadBundle.message("maven.download.sources")
      }
    } else {
      DownloadBundle.message("maven.download.docs")
    }
    myProgressListener.onEvent(myTaskId, StartBuildEventImpl(descriptor, message))
  }

  private fun createTaskId() = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)

  private fun newIssue(s: String): Boolean {
    return shownIssues.add(s)
  }

  @Synchronized
  fun finishDownload() {
    debugLog("Maven download: finishDownload")
    doFinish()
  }

  @Synchronized
  fun startDownloadTask() {
    startTask(myTaskId, DownloadBundle.message("maven.download.task"))
  }

  @Synchronized
  fun finishDownloadTask() {
    completeTask(myTaskId, DownloadBundle.message("maven.download.task"), SuccessResultImpl())
  }

  @Synchronized
  @ApiStatus.Internal
  fun addException(e: Throwable) {
    MavenLog.LOG.warn(e)
    hasErrors = true
    myProgressListener.onEvent(myTaskId, createMessageEvent(myProject, myTaskId, e))
  }

  @Synchronized
  private fun doFinish() {
    val tasks = myStartedSet.toList().asReversed()
    debugLog("Tasks $tasks are not completed! Force complete")
    tasks.forEach { completeTask(it.first, it.second, DerivedResultImpl()) }
    myProgressListener.onEvent(myTaskId, FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "",
                                                              if (hasErrors) FailureResultImpl() else DerivedResultImpl()))

    finished = true
    started = false
  }

  @Synchronized
  private fun startTask(parentId: Any, @NlsSafe taskName: String) {
    debugLog("Maven download: start $taskName")
    if (myStartedSet.add(parentId to taskName)) {
      myProgressListener.onEvent(myTaskId, StartEventImpl(taskName, parentId, System.currentTimeMillis(), taskName))
    }
  }

  @Synchronized
  private fun completeTask(parentId: Any, @NlsSafe taskName: String, result: EventResult) {
    hasErrors = hasErrors || result is FailureResultImpl

    debugLog("Maven download: complete $taskName with $result")
    if (myStartedSet.remove(parentId to taskName)) {
      myProgressListener.onEvent(myTaskId, FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result))
    }
  }

  companion object {
    private fun debugLog(s: String, exception: Throwable? = null) {
      MavenLog.LOG.debug(s, exception)
    }
  }
}