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

abstract class MavenSyncConsoleBase(protected val myProject: Project,
                                    private val myProgressListener: BuildProgressListener) {
  protected abstract val title: String
  protected abstract val message: String

  private val myTaskId = createTaskId()
  private val myStartedSet = LinkedHashSet<Pair<ExternalSystemTaskId, String>>()

  private var hasErrors = false

  protected fun createTaskId() = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)

  fun start() {
    val descriptor = DefaultBuildDescriptor(myTaskId, title, myProject.basePath!!, System.currentTimeMillis())
    descriptor.isActivateToolWindowWhenFailed = false
    descriptor.isActivateToolWindowWhenAdded = false
    myProgressListener.onEvent(myTaskId, StartBuildEventImpl(descriptor, message))
  }

  protected fun startTask(parentId: ExternalSystemTaskId, @NlsSafe taskName: String) {
    debugLog("Maven task: start $taskName")
    if (myStartedSet.add(parentId to taskName)) {
      myProgressListener.onEvent(myTaskId, StartEventImpl(taskName, parentId, System.currentTimeMillis(), taskName))
    }
  }

  protected fun startTask(@NlsSafe taskName: String) {
    startTask(myTaskId, taskName)
  }

  protected fun completeTask(parentId: ExternalSystemTaskId, @NlsSafe taskName: String, result: EventResult) {
    hasErrors = hasErrors || result is FailureResultImpl

    debugLog("Maven task: complete $taskName with $result")
    if (myStartedSet.remove(parentId to taskName)) {
      myProgressListener.onEvent(myTaskId, FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result))
    }
  }

  protected fun completeTask(@NlsSafe taskName: String, result: EventResult) {
    completeTask(myTaskId, taskName, result)
  }

  @ApiStatus.Internal
  fun addException(e: Throwable) {
    MavenLog.LOG.warn(e)
    hasErrors = true
    myProgressListener.onEvent(myTaskId, createMessageEvent(myProject, myTaskId, e))
  }

  fun finish() {
    val tasks = myStartedSet.toList().asReversed()
    debugLog("Tasks $tasks are not completed! Force complete")
    tasks.forEach { completeTask(it.first, it.second, DerivedResultImpl()) }
    myProgressListener.onEvent(myTaskId, FinishBuildEventImpl(myTaskId, null, System.currentTimeMillis(), "",
                                                              if (hasErrors) FailureResultImpl() else DerivedResultImpl()))
  }

  private fun debugLog(s: String, exception: Throwable? = null) {
    MavenLog.LOG.debug(s, exception)
  }
}

/**
 * An instance of this class is not supposed to be reused in multiple downloads
 */
class MavenDownloadConsole(myProject: Project,
                           myProgressListener: BuildProgressListener,
                           downloadSources: Boolean,
                           downloadDocs: Boolean) : MavenSyncConsoleBase(myProject, myProgressListener) {
  override val title: String = DownloadBundle.message("maven.download.title")
  override val message: String = if (downloadSources) {
    if (downloadDocs) {
      DownloadBundle.message("maven.download.sources.and.docs")
    }
    else {
      DownloadBundle.message("maven.download.sources")
    }
  }
  else {
    DownloadBundle.message("maven.download.docs")
  }

  fun startDownloadTask() {
    startTask(DownloadBundle.message("maven.download.task"))
  }

  fun finishDownloadTask() {
    completeTask(DownloadBundle.message("maven.download.task"), SuccessResultImpl())
  }
}