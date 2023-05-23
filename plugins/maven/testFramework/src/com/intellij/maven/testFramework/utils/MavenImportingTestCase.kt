// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenFolderResolver
import org.jetbrains.idea.maven.project.MavenProject

internal fun resolveFoldersAndImport(project: Project, mavenProjects: Collection<MavenProject>) {
  runBlocking {
    MavenFolderResolver(project).resolveFoldersAndImport(mavenProjects)
  }
}