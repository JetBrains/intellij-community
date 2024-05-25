// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.workspace

import com.intellij.ide.workspace.ImportedProjectSettings
import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import icons.MavenIcons
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider
import javax.swing.Icon

internal class MavenSubprojectHandler : SubprojectHandler {
  override fun getSubprojects(workspace: Project): List<Subproject> {
    return MavenProjectsManager.getInstance(workspace).projects
      .map { MavenSubproject(it, this) }
  }

  override fun canImportFromFile(file: VirtualFile): Boolean {
    return MavenOpenProjectProvider().canOpenProject(file)
  }

  override fun removeSubprojects(workspace: Project, subprojects: List<Subproject>) {
    val files = subprojects.flatMap { MavenUtil.collectFiles(listOf((it as MavenSubproject).mavenProject)) }
    MavenProjectsManager.getInstance(workspace).removeManagedFiles(files, null, null)
  }

  override fun importFromProject(project: Project): ImportedProjectSettings {
    // FIXME: does not work for new project: AbstractMavenModuleBuilder creates project in 'MavenUtil.runWhenInitialized' callback
    return MavenImportedProjectSettings(project)
  }

  override fun suppressGenericImportFor(module: Module): Boolean {
    return MavenProjectsManager.getInstance(module.project).findProject(module) != null
  }

  override val subprojectIcon: Icon
    get() = MavenIcons.MavenModule
}

private class MavenImportedProjectSettings(project: Project) : ImportedProjectSettings {
  val projectDir = requireNotNull(project.guessProjectDir())

  override suspend fun applyTo(workspace: Project): Boolean {
    val openProjectProvider = MavenOpenProjectProvider()
    if (openProjectProvider.canOpenProject(projectDir)) {
      openProjectProvider.forceLinkToExistingProjectAsync(projectDir, workspace)
      return true
    }
    return false
  }
}

private class MavenSubproject(val mavenProject: MavenProject, override val handler: SubprojectHandler) : Subproject {
  override val name: String get() = mavenProject.displayName
  override val projectPath: String get() = mavenProject.directory
}