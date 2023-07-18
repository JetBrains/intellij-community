// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import org.jetbrains.idea.maven.project.MavenProjectsTree

class MavenProfileWatcher(
  private val projectId: ExternalSystemProjectId,
  private val projectTracker: ExternalSystemProjectTracker,
  private val watcher: MavenProjectManagerWatcher
) {

  fun subscribeOnProfileChanges(parentDisposable: Disposable) {
    watcher.projectTree.addListener(object : MavenProjectsTree.Listener {
      override fun profilesChanged() {
        projectTracker.markDirty(projectId)
        projectTracker.scheduleChangeProcessing()
      }
    }, parentDisposable)
  }
}