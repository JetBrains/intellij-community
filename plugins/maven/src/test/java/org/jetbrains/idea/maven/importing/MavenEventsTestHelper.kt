// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.messages.MessageBusConnection
import org.junit.Assert

class MavenEventsTestHelper {
  private var beforeRootsChangedCount = 0
  private var rootsChangedCount = 0
  private var workspaceChangesCount = 0

  fun setUp(project: Project) {
    val connection: MessageBusConnection = project.messageBus.connect()
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun beforeRootsChange(event: ModuleRootEvent) {
        beforeRootsChangedCount++
      }

      override fun rootsChanged(event: ModuleRootEvent) {
        rootsChangedCount++
      }
    })

    connection.subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        val hasChanges = event.getAllChanges().iterator().hasNext()
        if (hasChanges) workspaceChangesCount++
      }
    })
  }

  fun tearDown() {
  }

  fun assertWorkspaceModelChanges(count: Int) {
    Assert.assertEquals(count, workspaceChangesCount)
    workspaceChangesCount = 0
  }

  fun assertRootsChanged(count: Int) {
    Assert.assertEquals(count, rootsChangedCount)
    Assert.assertEquals(rootsChangedCount, beforeRootsChangedCount)
    rootsChangedCount = 0
    beforeRootsChangedCount = 0
  }
}