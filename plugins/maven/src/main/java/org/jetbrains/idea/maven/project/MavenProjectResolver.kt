// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.platform.util.progress.RawProgressReporter
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException

interface MavenProjectResolver {
  @Throws(MavenProcessCanceledException::class)
  suspend fun resolve(mavenProjects: Collection<MavenProject>,
                      tree: MavenProjectsTree,
                      generalSettings: MavenGeneralSettings,
                      embeddersManager: MavenEmbeddersManager,
                      console: MavenConsole,
                      progressReporter: RawProgressReporter,
                      syncConsole: MavenSyncConsole?): MavenProjectResolutionResult

  @JvmRecord
  data class MavenProjectResolutionResult(val mavenProjectMap: Map<String, Collection<MavenProjectWithHolder>>)
  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenProjectResolver {
      return project.getService(MavenProjectResolver::class.java)
    }
  }
}
