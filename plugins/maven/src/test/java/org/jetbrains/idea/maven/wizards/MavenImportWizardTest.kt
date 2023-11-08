// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.maven.testFramework.utils.importMavenProjects
import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.write
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.jetbrains.idea.maven.project.BundledMaven3
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import java.nio.file.Path
import java.util.List

class MavenImportWizardTest : MavenProjectWizardTestCase() {
  override fun runInDispatchThread() = false

  override fun setUp() {
    super.setUp()
    MavenTestCase.assumeTestCanBeReusedForPreimport(this::class.java, name)
  }

  fun testImportModule() = runBlocking {
    val pom = createPom()
    val module = withContext(Dispatchers.EDT) { importModuleFrom(MavenProjectImportProvider(), pom.toString()) }
  }

  fun testImportProject() = runBlocking {
    val pom = createPom()
    val module = withContext(Dispatchers.EDT) { importProjectFrom(pom.toString(), null, MavenProjectImportProvider()) }

    val settings = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).settings.generalSettings
    val mavenHome = settings.getMavenHomeType()
    assertSame(BundledMaven3, mavenHome)
    assertTrue(MavenProjectsNavigator.getInstance(module.getProject()).groupModules)
    assertTrue(settings.isUseMavenConfig)
  }

  fun testImportProjectWithWrapper() = runBlocking {
    val pom = createPom()
    createMavenWrapper(pom,
                       "distributionUrl=https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.1/apache-maven-3.8.1-bin.zip")
    val module = withContext(Dispatchers.EDT) { importProjectFrom(pom.toString(), null, MavenProjectImportProvider()) }
    assertWithinTimeout {
      val mavenHome = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).settings.generalSettings.getMavenHomeType()
      assertSame(MavenWrapper, mavenHome)
    }
  }

  fun testImportProjectWithWrapperWithoutUrl() = runBlocking {
    val pom = createPom()
    createMavenWrapper(pom, "property1=value1")
    val module = withContext(Dispatchers.EDT) { importProjectFrom(pom.toString(), null, MavenProjectImportProvider()) }
    val mavenHome = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).settings.generalSettings.getMavenHomeType()
    assertSame(BundledMaven3, mavenHome)
  }

  fun testImportProjectWithManyPoms() = runBlocking {
    val pom1 = createPom("pom1.xml")
    val pom2 = pom1.parent.resolve("pom2.xml")
    pom2.write(MavenTestCase.createPomXml(
      """
      <groupId>test</groupId>
      <artifactId>project2</artifactId>
      <version>1</version>
      """.trimIndent()))
    val module = withContext(Dispatchers.EDT) { importProjectFrom(pom1.toString(), null, MavenProjectImportProvider()) }
    val project = module.getProject()
    assertWithinTimeout {
      val modules = ModuleManager.getInstance(project).modules
      val moduleNames = HashSet<String>()
      for (existingModule in modules) {
        moduleNames.add(existingModule.getName())
      }
      assertEquals(setOf("project", "project2"), moduleNames)
    }
    val projectsManager = MavenProjectsManager.getInstance(project)
    val mavenProjectNames = HashSet<String?>()
    for (p in projectsManager.getProjects()) {
      mavenProjectNames.add(p.mavenId.artifactId)
    }
    assertEquals(setOf("project", "project2"), mavenProjectNames)
  }

  fun testShouldStoreImlFileInSameDirAsPomXml() = runBlocking {
    val dir = tempDir.newPath("", true)
    val projectName = dir.toFile().getName()
    val pom = dir.resolve("pom.xml")
    pom.write(MavenTestCase.createPomXml(
      """
      <groupId>test</groupId>
      <artifactId>
      $projectName</artifactId>
      <version>1</version>
      """.trimIndent()))
    val provider = MavenProjectImportProvider()
    val module = withContext(Dispatchers.EDT) { importProjectFrom(pom.toString(), null, provider) }
    val project = module.getProject()
    waitForMavenImporting(project, LocalFileSystem.getInstance().findFileByNioFile(pom)!!)
    ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
    val modules = ModuleManager.getInstance(project).modules
    val imlFile = dir.resolve("$projectName.iml").toFile()
    val m = modules.filter {
      FileUtil.toSystemIndependentName(it.moduleFilePath) == FileUtil.toSystemIndependentName(imlFile.absolutePath)
    }
    TestCase.assertEquals(1, m.size)
  }

  private fun waitForMavenImporting(project: Project, file: VirtualFile) {
    val manager = MavenProjectsManager.getInstance(project)
    manager.waitForImportCompletion()
    importMavenProjects(manager, List.of(file))
    val promise = manager.waitForImportCompletion()
    runInEdtAndWait {
      PlatformTestUtil.waitForPromise(promise)
    }
  }

  companion object {
    private fun createMavenWrapper(pomPath: Path, context: String) {
      val fileName = pomPath.parent.resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties")
      fileName.write(context)
    }
  }
}
