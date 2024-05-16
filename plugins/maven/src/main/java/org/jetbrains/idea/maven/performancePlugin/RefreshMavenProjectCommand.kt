// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.autoimport.ProjectRefreshAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.toPromise
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenSyncListener

/**
 * The command refreshes project (like click 'Load Maven changes')
 * Syntax: %refreshMavenProject
 */
class RefreshMavenProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "refreshMavenProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val myProject = context.project
    val expectedFailurePattern = extractCommandArgument(PREFIX)
    val messageBus = ApplicationManager.getApplication().messageBus
    val callback = ActionCallback()
    var hasExpectedFailure = false
    messageBus.connect().subscribe(MavenSyncListener.TOPIC, object : MavenSyncListener {
      override fun syncFinished(project: Project) {
        if (myProject === project) {
          MavenProjectsManager.getInstance(project).projects.forEach {
            it.problems.forEach { problem ->
              val descriptor = problem.description ?: ""
              if (expectedFailurePattern.isNotEmpty() && descriptor.contains(expectedFailurePattern))
                hasExpectedFailure = true
              else
                callback.reject(problem.description)
            }
          }
          if (expectedFailurePattern.isEmpty() || hasExpectedFailure)
            callback.setDone()
          else
            callback.reject("Error $expectedFailurePattern was expected but not received")
        }
      }
    })
    ProjectRefreshAction.Manager.refreshProject(myProject)
    callback.toPromise().await()
  }

  override fun getName(): String {
    return NAME
  }
}