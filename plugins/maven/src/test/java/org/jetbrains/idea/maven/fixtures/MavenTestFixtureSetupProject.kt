// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.ide.actions.ImportProjectAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.maven.testFramework.fixtures.MavenTestFixture
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.xml.MavenBuildFileBuilder
import com.intellij.openapi.externalSystem.util.performActionAsync
import com.intellij.openapi.externalSystem.util.performOpenAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.actions.AddFileAsMavenProjectAction
import org.jetbrains.idea.maven.project.actions.AddManagedFilesAction
import org.jetbrains.idea.maven.utils.MavenUtil.SYSTEM_ID

// Ported from MavenSetupProjectTestCase.

data class ProjectInfo(val projectFile: VirtualFile, val modules: List<String>) {
  constructor(projectFile: VirtualFile, vararg modules: String) : this(projectFile, modules.toList())
}

fun MavenTestFixture.generateProject(id: String): ProjectInfo {
  val name = "${System.currentTimeMillis()}-$id"
  createProjectSubFile("$name-external-module/pom.xml", MavenBuildFileBuilder("$name-external-module").generate())
  createProjectSubFile("$name-project/$name-module/pom.xml", MavenBuildFileBuilder("$name-module").generate())
  val buildScript = MavenBuildFileBuilder("$name-project")
    .withPomPackaging()
    .withModule("$name-module")
    .withModule("../$name-external-module")
    .generate()
  val projectFile = createProjectSubFile("$name-project/pom.xml", buildScript)
  return ProjectInfo(projectFile, "$name-project", "$name-module", "$name-external-module")
}

suspend fun MavenTestFixture.openPlatformProjectAsync(projectDirectory: VirtualFile): Project {
  return closeOpenedProjectsIfFailAsync {
    ProjectManagerEx.getInstanceEx().openProjectAsync(
      projectIdentityFile = projectDirectory.toNioPath(),
      options = OpenProjectTask {
        forceOpenInNewFrame = true
        useDefaultProjectAsTemplate = false
      }
    )!!
  }
}

suspend fun MavenTestFixture.importProjectActionAsync(projectFile: VirtualFile): Project {
  return performOpenAction(
    action = ImportProjectAction(),
    systemId = SYSTEM_ID,
    selectedFile = projectFile
  )
}

suspend fun MavenTestFixture.attachProjectAsync(project: Project, projectFile: VirtualFile): Project {
  performActionAsync(
    action = { withContext(Dispatchers.IO) { AddManagedFilesAction().actionPerformedAsync(it) } },
    project = project,
    systemId = SYSTEM_ID,
    selectedFile = projectFile
  )
  return project
}

suspend fun MavenTestFixture.attachProjectFromScriptAsync(project: Project, projectFile: VirtualFile): Project {
  performActionAsync(
    action = { withContext(Dispatchers.IO) { AddFileAsMavenProjectAction().actionPerformedAsync(it) } },
    project = project,
    systemId = SYSTEM_ID,
    selectedFile = projectFile,
  )
  return project
}

suspend fun MavenTestFixture.waitForImport(action: suspend () -> Project): Project {
  val p = action()
  TestObservation.awaitConfiguration(p)
  return p
}

fun MavenTestFixture.getGeneralSettings(project: Project): MavenGeneralSettings {
  return MavenWorkspaceSettingsComponent.getInstance(project)
    .settings.getGeneralSettings()
}

suspend fun MavenTestFixture.assertProjectState(project: Project, vararg projectsInfo: ProjectInfo) = assertWithinTimeout {
  assertProjectStructure(project, *projectsInfo)
}

private fun MavenTestFixture.assertProjectStructure(project: Project, vararg projectsInfo: ProjectInfo) {
  assertModules(project, *projectsInfo.flatMap { it.modules }.toTypedArray())
}
