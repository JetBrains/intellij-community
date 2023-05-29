// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenProjectManagerListenerToBusBridge(project: Project) : MavenProjectsManager.Listener {

  private val eventPublisher = project.messageBus.syncPublisher(MavenImportingManager.LEGACY_PROJECT_MANAGER_LISTENER)
  override fun activated() {
    eventPublisher.activated()
  }

  override fun importAndResolveScheduled() {
    eventPublisher.importAndResolveScheduled();
  }

  override fun projectImportCompleted() {
    eventPublisher.projectImportCompleted();
  }

}