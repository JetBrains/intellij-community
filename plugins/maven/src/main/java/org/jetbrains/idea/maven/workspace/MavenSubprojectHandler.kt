// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.workspace

import com.intellij.ide.workspace.ImportedProjectSettings
import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFile
import icons.MavenIcons
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider
import javax.swing.Icon

internal class MavenSubprojectHandler : SubprojectHandler {
  override fun getSubprojects(project: Project): List<Subproject> {
    return MavenProjectsManager.getInstance(project).projects
      .map { MavenSubproject(project, it, this) }
  }

  override fun canImportFromFile(project: Project, file: VirtualFile): Boolean {
    return MavenOpenProjectProvider().canOpenProject(file)
  }

  override fun removeSubprojects(subprojects: List<Subproject>) {
    val workspace = subprojects.firstOrNull()?.workspace ?: return
    val files = subprojects.flatMap { MavenUtil.collectFiles(listOf((it as MavenSubproject).mavenProject)) }
    MavenProjectsManager.getInstance(workspace).removeManagedFiles(files, null, null)
  }

  override fun importFromProject(project: Project, newWorkspace: Boolean): ImportedProjectSettings {
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
  val projectDir = project.guessProjectDir()

  override suspend fun applyTo(workspace: Project) {
    val openProjectProvider = MavenOpenProjectProvider()
    if (openProjectProvider.canOpenProject(projectDir!!)) {
      StartupManager.getInstance(workspace).runAfterOpened {
        MavenCoroutineScopeProvider.getCoroutineScope(workspace).launch {
          openProjectProvider.forceLinkToExistingProjectAsync(projectDir, workspace)
        }
      }
    }
  }
}

private class MavenSubproject(override val workspace: Project, val mavenProject: MavenProject, override val handler: SubprojectHandler) : Subproject {
  override val name: String get() = mavenProject.displayName
  override val projectPath: String get() = mavenProject.directory

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MavenSubproject

    if (workspace != other.workspace) return false
    if (mavenProject != other.mavenProject) return false

    return true
  }

  override fun hashCode(): Int {
    var result = workspace.hashCode()
    result = 31 * result + mavenProject.hashCode()
    return result
  }
}