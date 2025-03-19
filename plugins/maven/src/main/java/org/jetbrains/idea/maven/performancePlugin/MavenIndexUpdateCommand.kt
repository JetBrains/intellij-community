// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.toPromise
import org.jetbrains.idea.maven.indices.IndexChangeProgressListener
import org.jetbrains.idea.maven.indices.MavenIndexUtils
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.model.RepositoryKind
import org.jetbrains.idea.maven.server.MavenIndexUpdateState

/**
 * The command updates maven indexes for [maven_repo_url]. If [maven_repo_url] is empty the local repo will be indexed
 * Syntax: %mavenIndexUpdate maven_repo_url
 */
class MavenIndexUpdateCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "mavenIndexUpdate"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val actionCallback = ActionCallback()
    val project = context.project

    val mavenRepo = extractCommandArgument(PREFIX).let {
      if (it.isEmpty()) MavenIndexUtils.getLocalRepository(project)
      else {
        val id = it.substringAfter("https://").replace("/", "-")
        MavenRepositoryInfo(id, id.substringBefore("-"), it, RepositoryKind.REMOTE)
      }
    }!!
    ApplicationManager.getApplication().messageBus.connect().subscribe(
      MavenSystemIndicesManager.TOPIC, object : IndexChangeProgressListener {
      override fun indexStatusChanged(state: MavenIndexUpdateState) {
        if (state.myState == MavenIndexUpdateState.State.SUCCEED) {
          actionCallback.setDone()
        }
        if (state.myState == MavenIndexUpdateState.State.FAILED) {
          actionCallback.reject("Failed while updating maven indexes ${state.myError}")
        }
      }
    })

    MavenSystemIndicesManager.getInstance().updateIndexContent(mavenRepo, project)

    actionCallback.toPromise().await()
  }

  override fun getName(): String {
    return NAME
  }
}