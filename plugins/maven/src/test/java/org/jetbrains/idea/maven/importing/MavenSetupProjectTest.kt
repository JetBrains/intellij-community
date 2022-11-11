// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.maven.testFramework.xml.MavenBuildFileBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTest
import com.intellij.openapi.externalSystem.importing.ExternalSystemSetupProjectTestCase
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.actions.AddFileAsMavenProjectAction
import org.jetbrains.idea.maven.project.actions.AddManagedFilesAction
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.utils.MavenUtil.SYSTEM_ID
import org.junit.Test

class MavenSetupProjectTest : ExternalSystemSetupProjectTest, MavenImportingTestCase() {
  override fun getSystemId(): ProjectSystemId = SYSTEM_ID

  override fun runInDispatchThread() = false

  @Test
  fun `test settings are not reset`() = runBlocking {
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

  override suspend fun attachProject(project: Project, projectFile: VirtualFile): Project {
    performAction(AddManagedFilesAction(), project, selectedFile = projectFile)
    waitForImportCompletion(project)
    return project
  }

  override suspend fun attachProjectFromScript(project: Project, projectFile: VirtualFile): Project {
    performAction(AddFileAsMavenProjectAction(), project, selectedFile = projectFile)
    waitForImportCompletion(project)
    return project
  }

  override suspend fun waitForImport(action: suspend () -> Project): Project {
    val p = action()
    waitForImportCompletion(p)
    return p
  }

  private suspend fun waitForImportCompletion(project: Project) {
    withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }

    val projectManager = MavenProjectsManager.getInstance(project)
    projectManager.initForTests()
    if (isNewImportingProcess) {
      val deferred = withContext(Dispatchers.EDT) {
        MavenImportingManager.getInstance(project).getImportFinishPromise()
      }.asDeferred()
      val importFinishedContext = deferred.await()
      importFinishedContext.error?.let { throw it }
    }
    else {
      withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
        projectManager.waitForResolvingCompletion()
        projectManager.performScheduledImportInTests()
      }
    }
  }
}