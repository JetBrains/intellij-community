// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.ide.actions.ImportProjectAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.maven.testFramework.xml.MavenBuildFileBuilder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.externalSystem.util.performAction
import com.intellij.openapi.externalSystem.util.performOpenAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.utils.module.assertModules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.actions.AddFileAsMavenProjectAction
import org.jetbrains.idea.maven.project.actions.AddManagedFilesAction
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.utils.MavenUtil.SYSTEM_ID

abstract class MavenSetupProjectTestCase : MavenMultiVersionImportingTestCase() {

  override fun runInDispatchThread() = false

  fun generateProject(id: String): ProjectInfo {
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

  suspend fun openPlatformProjectAsync(projectDirectory: VirtualFile): Project {
    return closeOpenedProjectsIfFailAsync {
      ProjectManagerEx.getInstanceEx().openProjectAsync(
        projectStoreBaseDir = projectDirectory.toNioPath(),
        options = OpenProjectTask {
          forceOpenInNewFrame = true
          useDefaultProjectAsTemplate = false
          isRefreshVfsNeeded = false
        }
      )!!
    }
  }

  suspend fun importProjectAsync(projectFile: VirtualFile): Project {
    return performOpenAction(
      action = ImportProjectAction(),
      systemId = SYSTEM_ID,
      selectedFile = projectFile
    )
  }

  suspend fun attachProjectAsync(project: Project, projectFile: VirtualFile): Project {
    performAction(
      action = AddManagedFilesAction(),
      project = project,
      systemId = SYSTEM_ID,
      selectedFile = projectFile
    )
    return project
  }

  suspend fun attachProjectFromScriptAsync(project: Project, projectFile: VirtualFile): Project {
    performAction(
      action = AddFileAsMavenProjectAction(),
      project = project,
      systemId = SYSTEM_ID,
      selectedFile = projectFile,
      blocking = true
    )
    return project
  }

  suspend fun waitForImport(action: suspend () -> Project): Project {
    val p = action()
    waitForImportCompletion(p)
    return p
  }

  private suspend fun waitForImportCompletion(project: Project) {
    withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
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
      withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
        projectManager.waitForReadingCompletion()
        //projectManager.performScheduledImportInTests()
        projectManager.waitForImportCompletion()
      }
    }
  }

  fun getGeneralSettings(project: Project): MavenGeneralSettings {
    return MavenWorkspaceSettingsComponent.getInstance(project)
      .settings.getGeneralSettings()
  }

  fun assertProjectState(project: Project, vararg projectsInfo: ProjectInfo) {
    assertProjectStructure(project, *projectsInfo)
  }

  private fun assertProjectStructure(project: Project, vararg projectsInfo: ProjectInfo) {
    assertModules(project, *projectsInfo.flatMap { it.modules }.toTypedArray())
  }

  data class ProjectInfo(val projectFile: VirtualFile, val modules: List<String>) {
    constructor(projectFile: VirtualFile, vararg modules: String) : this(projectFile, modules.toList())
  }
}