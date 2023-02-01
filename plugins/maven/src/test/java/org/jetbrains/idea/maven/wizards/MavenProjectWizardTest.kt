// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.NewProjectWizardTestCase
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizardData.Companion.javaData
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.languageData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.module.assertModules
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import java.io.File

class MavenProjectWizardTest : NewProjectWizardTestCase() {
  private lateinit var mySdk: Sdk

  override fun setUp() {
    super.setUp()

    mySdk = ExternalSystemJdkProvider.getInstance().internalJdk
    val jdkTable = ProjectJdkTable.getInstance()
    runWriteActionAndWait {
      jdkTable.addJdk(mySdk, testRootDisposable)
    }
    Disposer.register(testRootDisposable, Disposable {
      runWriteActionAndWait {
        jdkTable.removeJdk(mySdk)
      }
    })

    VfsRootAccess.allowRootAccess(testRootDisposable, PathManager.getConfigPath())
    val javaHome = ExternalSystemJdkUtil.getJavaHome()
    if (javaHome != null) {
      VfsRootAccess.allowRootAccess(testRootDisposable, javaHome)
    }
  }

  override fun createWizard(project: Project?) {
    if (project != null) {
      val localFileSystem = LocalFileSystem.getInstance()
      localFileSystem.refreshAndFindFileByPath(project.basePath!!)
    }
    val directory = if (project == null) createTempDirectoryWithSuffix("New").toFile() else null
    if (myWizard != null) {
      Disposer.dispose(myWizard.disposable)
      myWizard = null
    }
    myWizard = createWizard(project, directory)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun waitForMavenImporting(project: Project, file: VirtualFile) {
    val manager = MavenProjectsManager.getInstance(project)
    if (!MavenUtil.isLinearImportEnabled()) {
      manager.waitForImportCompletion()
      ApplicationManager.getApplication().invokeAndWait {
        manager.scheduleImportInTests(listOf(file))
        manager.importProjects()
      }
    }
    val promise = manager.waitForImportCompletion()
    PlatformTestUtil.waitForPromise(promise)
  }

  fun `test when module is created then its pom is unignored`() {
    // create project
    val project = createProjectFromTemplate {
      it.baseData!!.name = "project"
      it.javaBuildSystemData!!.buildSystem = "Maven"
      it.javaData!!.sdk = mySdk
    }
    val projectPath = project.basePath
    assertModules(project, "project")

    // import project
    val module = ModuleManager.getInstance(project).findModuleByName("project")!!
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val projectPomPath = File(projectPath, "pom.xml")
    val projectPomFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectPomPath)!!
    waitForMavenImporting(project, projectPomFile)
    assertSize(1, mavenProjectsManager.projects)

    // ignore pom
    val modulePomPath = "$projectPath/untitled/pom.xml"
    val ignoredPoms = listOf(modulePomPath)
    mavenProjectsManager.ignoredFilesPaths = ignoredPoms
    assertEquals(ignoredPoms, mavenProjectsManager.ignoredFilesPaths)

    // create module
    val mavenProject = mavenProjectsManager.findProject(module)
    createModuleFromTemplate(project) {
      it.languageData!!.language = "Java"
      it.javaBuildSystemData!!.buildSystem = "Maven"
      it.javaMavenData!!.parentData = mavenProject
      assertEquals("untitled", it.baseData!!.name)
    }
    assertModules(project, "project", "untitled")

    // verify pom unignored
    assertSize(0, mavenProjectsManager.ignoredFilesPaths)
  }

}