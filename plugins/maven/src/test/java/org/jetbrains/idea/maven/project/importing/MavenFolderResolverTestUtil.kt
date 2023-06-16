// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenFolderResolver
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges

internal fun resolveFoldersSync(project: Project, mavenProjects: Collection<MavenProject>) : Map<MavenProject, MavenProjectChanges> {
  return runBlockingMaybeCancellable {
    return@runBlockingMaybeCancellable MavenFolderResolver(project).resolveFolders(mavenProjects)
  }
}