// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.tasks.Task
import com.intellij.tasks.TaskManager
import com.intellij.tasks.context.WorkingContextManager
import java.util.*

class TaskCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    TaskCheckinHandler(panel)

  private class TaskCheckinHandler(val panel: CheckinProjectPanel) : CheckinHandler(), DumbAware {
    override fun checkinSuccessful() {
      val message = panel.getCommitMessage()
      val project = panel.getProject()
      val manager = TaskManager.getManager(project) as TaskManagerImpl
      if (manager.getState().saveContextOnCommit) {
        val task = findTaskInRepositories(message, manager)
                   ?: manager.createLocalTask(message)
                     .also { (it as LocalTaskImpl).isClosed = true }
        val localTask = manager.addTask(task)
        localTask.setUpdated(Date())

        ApplicationManager.getApplication().invokeLater(
          { WorkingContextManager.getInstance(project).saveContext(localTask) }, project.getDisposed())
      }
    }
  }
}

private fun findTaskInRepositories(message: String, manager: TaskManager): Task? {
  val repositories = manager.getAllRepositories()
  for (repository in repositories) {
    val id = repository.extractId(message) ?: continue
    manager.findTask(id)?.let { return it }
    try {
      repository.findTask(id)?.let { return it }
    }
    catch (_: Exception) {
    }
  }
  return null
}
