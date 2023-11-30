// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider

open class DownloadAllSourcesAndDocsAction @JvmOverloads constructor(private val mySources: Boolean = true,
                                                                     private val myDocs: Boolean = true) : MavenProjectsManagerAction() {
  override fun perform(manager: MavenProjectsManager) {
    val cs = MavenCoroutineScopeProvider.getCoroutineScope(manager.project)
    cs.launch {
      manager.downloadArtifacts(manager.projects, null, mySources, myDocs)
    }
  }
}