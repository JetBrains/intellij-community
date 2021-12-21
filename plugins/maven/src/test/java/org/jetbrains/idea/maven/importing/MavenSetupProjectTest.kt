// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTest
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTestCase
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import junit.framework.TestCase
import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.maven.testFramework.xml.MavenBuildFileBuilder
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.actions.AddFileAsMavenProjectAction
import org.jetbrains.idea.maven.project.actions.AddManagedFilesAction
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.utils.MavenUtil.SYSTEM_ID
import org.junit.Test

class MavenSetupProjectTest : ExternalSystemSetupProjectTest, MavenImportingTestCase() {
  override fun getSystemId(): ProjectSystemId = SYSTEM_ID

  @Test
  fun `test settings are not reset`() {
    val projectInfo = generateProject("A")
    val linkedProjectInfo = generateProject("L")
    waitForImport {
      openProjectFrom(projectInfo.projectFile)
    }.use {
      assertModules(it, projectInfo)
      MavenWorkspaceSettingsComponent.getInstance(it).settings.getGeneralSettings().isWorkOffline = true
      waitForImport {
        attachProject(it, linkedProjectInfo.projectFile)
      }
      assertModules(it, projectInfo, linkedProjectInfo)
      TestCase.assertTrue(MavenWorkspaceSettingsComponent.getInstance(it).settings.getGeneralSettings().isWorkOffline)
    }
  }

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

  override fun attachProject(project: Project, projectFile: VirtualFile): Project {
    AddManagedFilesAction().perform(project, selectedFile = projectFile)
    waitForImportCompletion(project)
    return project
  }

  override fun attachProjectFromScript(project: Project, projectFile: VirtualFile): Project {
    AddFileAsMavenProjectAction().perform(project, selectedFile = projectFile)
    waitForImportCompletion(project)
    return project
  }

  override fun waitForImport(action: () -> Project): Project {
    val p = action()
    waitForImportCompletion(p)
    return p
  }

  private fun waitForImportCompletion(project: Project) {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    val projectManager = MavenProjectsManager.getInstance(project)
    projectManager.initForTests()
    if (isNewImportingProcess) {
      val promise = MavenImportingManager.getInstance(project).getImportFinishPromise()
      PlatformTestUtil.waitForPromise(promise)
    }
    else {
      ApplicationManager.getApplication().invokeAndWait {
        projectManager.waitForResolvingCompletion()
        projectManager.performScheduledImportInTests()
      }
    }

  }
}