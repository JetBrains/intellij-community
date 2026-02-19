// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

class MavenImportListenerBridge : MavenSyncListener {
  override fun importStarted(project: Project) {
    project.messageBus.syncPublisher(MavenImportListener.TOPIC).importStarted()
  }

  override fun importFinished(project: Project, importedProjects: Collection<MavenProject>, newModules: List<Module>) {
    project.messageBus.syncPublisher(MavenImportListener.TOPIC).importFinished(importedProjects, newModules)
  }
}
