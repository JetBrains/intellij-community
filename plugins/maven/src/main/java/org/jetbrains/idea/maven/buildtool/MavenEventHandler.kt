// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.server.MavenArtifactEvent
import org.jetbrains.idea.maven.server.MavenServerConsoleEvent
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator
import org.jetbrains.idea.maven.utils.MavenLog

interface MavenEventHandler {
  fun handleConsoleEvents(consoleEvents: List<MavenServerConsoleEvent>)
  fun handleDownloadEvents(downloadEvents: List<MavenArtifactEvent>)
}

@ApiStatus.Internal
interface MavenBuildIssueHandler {
  fun addBuildIssue(issue: BuildIssue, kind: MessageEvent.Kind)
}

object MavenLogEventHandler : MavenEventHandler {
  override fun handleConsoleEvents(consoleEvents: List<MavenServerConsoleEvent>) {
    for (e in consoleEvents) {
      val message = e.message
      when (e.level) {
        MavenServerConsoleIndicator.LEVEL_DEBUG -> MavenLog.LOG.debug(message)
        MavenServerConsoleIndicator.LEVEL_INFO -> MavenLog.LOG.info(message)
        else -> MavenLog.LOG.warn(message)
      }
      val throwable = e.throwable
      if (null != throwable) {
        MavenLog.LOG.warn(throwable)
      }
    }
  }

  override fun handleDownloadEvents(downloadEvents: List<MavenArtifactEvent>) {
    for (e in downloadEvents) {
      val id = e.dependencyId
      when (e.artifactEventType) {
        MavenArtifactEvent.ArtifactEventType.DOWNLOAD_STARTED ->
          MavenLog.LOG.debug("Download started: $id")

        MavenArtifactEvent.ArtifactEventType.DOWNLOAD_COMPLETED ->
          MavenLog.LOG.debug("Download completed: $id")

        MavenArtifactEvent.ArtifactEventType.DOWNLOAD_FAILED ->
          MavenLog.LOG.debug("Download failed: $id \n${e.errorMessage} \n${e.stackTrace}")
      }
    }
  }

}