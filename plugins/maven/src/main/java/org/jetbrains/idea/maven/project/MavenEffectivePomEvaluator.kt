// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import org.jetbrains.idea.maven.utils.MavenEelUtil.resolveUsingEel
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import kotlin.io.path.Path

class MavenEffectivePomEvaluator {
  companion object {
    @JvmStatic
    suspend fun evaluateEffectivePom(project: Project, mavenProject: MavenProject): String? {
      return withBackgroundProgress(project, MavenProjectBundle.message("maven.project.importing.evaluating.effective.pom"), true) {
        val baseDir = MavenUtil.getBaseDir(mavenProject.directoryFile).toString()
        val embeddersManager = MavenProjectsManager.getInstance(project).embeddersManager
        val embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, baseDir)
        try {
          val profiles = mavenProject.activatedProfilesIds
          val virtualFile = mavenProject.file
          val projectFile = resolveUsingEel(project, { File(virtualFile.path) }) {
            File(it.mapper.getOriginalPath(Path(virtualFile.path)).toString())
          }
          return@withBackgroundProgress embedder.evaluateEffectivePom(projectFile, profiles.enabledProfiles, profiles.disabledProfiles)
        }
        catch (e: Exception) {
          MavenLog.LOG.error(e)
          return@withBackgroundProgress null
        }
      }
    }
  }
}