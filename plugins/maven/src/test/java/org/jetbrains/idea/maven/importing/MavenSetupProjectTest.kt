// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTest
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTestCase
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.MavenImportingTestCase
import org.jetbrains.idea.maven.importing.xml.MavenBuildFileBuilder
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.actions.AddFileAsMavenProjectAction
import org.jetbrains.idea.maven.project.actions.AddManagedFilesAction
import org.jetbrains.idea.maven.utils.MavenUtil.SYSTEM_ID

class MavenSetupProjectTest : ExternalSystemSetupProjectTest, MavenImportingTestCase() {
  override fun getSystemId(): ProjectSystemId = SYSTEM_ID

  override fun generateProject(id: String): ExternalSystemSetupProjectTestCase.ProjectInfo {
    val name = "${System.currentTimeMillis()}-$id"
    createProjectSubFile("$name-external-module/pom.xml", MavenBuildFileBuilder("$name-external-module").generate())
    createProjectSubFile("$name-project/$name-module/pom.xml", MavenBuildFileBuilder("$name-module").generate())
    val buildScript = MavenBuildFileBuilder("$name-project")
      .withPomPackaging()
      .withModule("$name-module")
      .withModule("../$name-external-module")
      .generate()
    val projectFile = createProjectSubFile("$name-project/pom.xml", buildScript)
    return ExternalSystemSetupProjectTestCase.ProjectInfo(projectFile, "$name-project", "$name-module", "$name-external-module")
  }

  override fun assertDefaultProjectSettings(project: Project) {
  }

  override fun doAttachProject(project: Project, projectFile: VirtualFile) {
    AddManagedFilesAction().perform(project, selectedFile = projectFile)
  }

  override fun doAttachProjectFromScript(project: Project, projectFile: VirtualFile) {
    AddFileAsMavenProjectAction().perform(project, selectedFile = projectFile)
  }

  override fun waitForImportCompletion(project: Project) {
    val projectManager = MavenProjectsManager.getInstance(project)
    ApplicationManager.getApplication().invokeAndWait {
      projectManager.waitForResolvingCompletion()
      projectManager.performScheduledImportInTests()
    }
  }
}