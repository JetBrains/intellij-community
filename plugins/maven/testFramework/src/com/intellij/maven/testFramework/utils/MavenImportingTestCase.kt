// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenFolderResolver
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsManager

fun resolveFoldersAndImport(project: Project, mavenProjects: Collection<MavenProject>) {
  runWithModalProgressBlocking(project, "") {
    MavenFolderResolver(project).resolveFoldersAndImport(mavenProjects)
  }
}

fun importMavenProjectsSync(mavenProjectsManager: MavenProjectsManager) {
  runBlocking {
    mavenProjectsManager.importMavenProjects(emptyMap())
  }
}

fun importMavenProjectsSync(mavenProjectsManager: MavenProjectsManager, projectFiles: List<VirtualFile>) {
  val toImport= mutableMapOf<MavenProject, MavenProjectChanges>()
  for (each in projectFiles) {
    val project = mavenProjectsManager.findProject(each)
    if (project != null) {
      toImport[project] = MavenProjectChanges.ALL
    }
  }
  runBlocking {
    mavenProjectsManager.importMavenProjects(toImport)
  }
}

fun importMavenProjectsSync(mavenProjectsManager: MavenProjectsManager,
                            modelsProvider: IdeModifiableModelsProvider,
                            projectFiles: List<VirtualFile>) {
  val toImport = mutableMapOf<MavenProject, MavenProjectChanges>()
  for (each in projectFiles) {
    val project = mavenProjectsManager.findProject(each)
    if (project != null) {
      toImport[project] = MavenProjectChanges.ALL
    }
  }
  mavenProjectsManager.importMavenProjectsSync(toImport, modelsProvider)
}

