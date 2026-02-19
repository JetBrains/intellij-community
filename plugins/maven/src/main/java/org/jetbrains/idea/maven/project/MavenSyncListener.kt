// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

interface MavenSyncListener {
  /**
   * Called when Maven sync is started
   */
  fun syncStarted(project: Project) {}

  /**
   * Called when Maven sync is finished
   */
  fun syncFinished(project: Project) {}

  /**
   * Called when Maven model is collected and IDEA is ready to import Maven model into its own Workspace model
   */
  fun importStarted(project: Project) {}

  /**
   * Workspace model is committed, project structure is created. Please note, that certain related activities
   * may not be completed yet, e.g., plugin resolution and source downloading
   */
  fun importFinished(project: Project, importedProjects: Collection<MavenProject>, newModules: List<Module>) {}

  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<MavenSyncListener> = Topic.create("Maven sync notifications", MavenSyncListener::class.java)
  }
}
