// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.workspace

import com.intellij.ide.workspace.ImportedProjectSettings
import com.intellij.ide.workspace.Subproject
import com.intellij.ide.workspace.SubprojectHandler
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenRoamableSettings
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider

internal class MavenSubprojectHandler : SubprojectHandler {
  override fun getSubprojects(project: Project): List<Subproject> {
    return MavenProjectsManager.getInstance(project).projects
      .map { MavenSubproject(project, it) }
  }

  override fun getSubprojectFor(module: Module): Subproject? {
    val mavenProject = MavenProjectsManager.getInstance(module.project).findProject(module) ?: return null
    return MavenSubproject(module.project, mavenProject)
  }

  override fun canImportFromFile(project: Project, file: VirtualFile): Boolean {
    return MavenActionUtil.isMavenProjectFile(file)
  }

  override fun importFromFile(project: Project, file: VirtualFile) {
    MavenUtil.isProjectTrustedEnoughToImport(project)
    MavenOpenProjectProvider().linkToExistingProject(file, project)
  }

  override fun importFromProject(project: Project, newWorkspace: Boolean): ImportedProjectSettings {
    // FIXME: does not work for new project: AbstractMavenModuleBuilder creates project in 'MavenUtil.runWhenInitialized' callback
    return MavenImportedProjectSettings(project)
  }

  override fun suppressGenericImportFor(module: Module): Boolean {
    return MavenProjectsManager.getInstance(module.project).findProject(module) != null
  }
}

private class MavenImportedProjectSettings(project: Project) : ImportedProjectSettings {
  val roamableSettings: MavenRoamableSettings = MavenProjectsManager.getInstance(project).roamableSettings
  val projectDir = project.guessProjectDir()

  override suspend fun applyTo(workspace: Project) {
    val openProjectProvider = MavenOpenProjectProvider()
    if (roamableSettings.originalFiles.isEmpty() && openProjectProvider.canOpenProject(projectDir!!)) {
      openProjectProvider.linkToExistingProjectAsync(projectDir, workspace)
      return
    }
    val manager = MavenProjectsManager.getInstance(workspace)
    manager.applyRoamableSettings(roamableSettings)
  }
}

private class MavenSubproject(override val workspace: Project, val mavenProject: MavenProject) : Subproject {
  override val name: String get() = mavenProject.displayName
  override val projectPath: String get() = mavenProject.path

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

  override fun getModules(): List<Module> {
    val manager = MavenProjectsManager.getInstance(workspace)
    val mavenModules = manager.getModules(mavenProject) + mavenProject
    return mavenModules.mapNotNull { manager.findModule(it) }
  }

  override fun removeSubproject() {
    val files = MavenUtil.collectFiles(listOf(mavenProject))
    MavenProjectsManager.getInstance(workspace).removeManagedFiles(files)
  }
}