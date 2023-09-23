// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.maven.testFramework.utils.importMavenProjects
import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.io.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.jetbrains.idea.maven.project.BundledMaven3
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.project.importing.MavenImportFinishedContext
import org.jetbrains.idea.maven.project.importing.MavenImportingManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path
import java.util.List
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.function.Consumer

class MavenImportWizardTest : MavenProjectWizardTestCase() {
  override fun runInDispatchThread() = false

  fun testImportModule() = runBlocking {
    val pom = createPom()
    val module = withContext(Dispatchers.EDT) { importModuleFrom(MavenProjectImportProvider(), pom.toString()) }
    afterImportFinished(myProject) {
      val created = it!!.context!!.modulesCreated
      Assertions.assertThat(created).singleElement().matches { m: Module -> m.getName() == "project" }
    }
  }

  fun testImportProject() = runBlocking {
    val pom = createPom()
    val module = withContext(Dispatchers.EDT) { importProjectFrom(pom.toString(), null, MavenProjectImportProvider()) }
    afterImportFinished(module.getProject()) {
      Assertions.assertThat(ModuleManager.getInstance(it!!.project).modules).hasOnlyOneElementSatisfying { m: Module -> Assertions.assertThat(m.getName()).isEqualTo("project") }
    }
    val settings = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).settings.generalSettings
    val mavenHome = settings.getMavenHomeType()
    assertSame(BundledMaven3, mavenHome)
    assertTrue(MavenProjectsNavigator.getInstance(module.getProject()).groupModules)
    assertTrue(settings.isUseMavenConfig)
  }

  fun testImportProjectWithWrapper() = runBlocking {
    val pom = createPom()
    createMavenWrapper(pom, "distributionUrl=https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.1/apache-maven-3.8.1-bin.zip")
    val module = withContext(Dispatchers.EDT) { importProjectFrom(pom.toString(), null, MavenProjectImportProvider()) }
    afterImportFinished(module.getProject()) {
      Assertions.assertThat(ModuleManager.getInstance(it!!.project).modules).hasOnlyOneElementSatisfying { m: Module -> Assertions.assertThat(m.getName()).isEqualTo("project") }
    }
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
    afterImportFinished(module.getProject()) {
      Assertions.assertThat(ModuleManager.getInstance(it!!.project).modules).hasOnlyOneElementSatisfying { m: Module -> Assertions.assertThat(m.getName()).isEqualTo("project") }
    }
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
    if (MavenUtil.isLinearImportEnabled()) {
      afterImportFinished(createdProject) { c: MavenImportFinishedContext? ->
        val paths = MavenProjectsManager.getInstance(c!!.project).projectsTreeForTests.getExistingManagedFiles().map { it.toNioPath() }
        assertEquals(2, paths.size)
        UsefulTestCase.assertContainsElements(paths, pom1, pom2)
      }
    }
    else {
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
    Assertions.assertThat(modules).hasOnlyOneElementSatisfying { m: Module ->
      PlatformTestUtil.assertPathsEqual(m.moduleFilePath, imlFile.absolutePath)
    }
  }

  private fun waitForMavenImporting(project: Project, file: VirtualFile) {
    val manager = MavenProjectsManager.getInstance(project)
    if (!MavenUtil.isLinearImportEnabled()) {
      manager.waitForImportCompletion()
      importMavenProjects(manager, List.of(file))
    }
    val promise = manager.waitForImportCompletion()
    PlatformTestUtil.waitForPromise(promise)
  }

  private fun afterImportFinished(p: Project, after: Consumer<MavenImportFinishedContext?>) {
    if (MavenUtil.isLinearImportEnabled()) {
      val promise = MavenImportingManager.getInstance(p).getImportFinishPromise()
      PlatformTestUtil.waitForPromise(promise)
      try {
        after.accept(promise.blockingGet(0))
      }
      catch (e: TimeoutException) {
        throw RuntimeException(e)
      }
      catch (e: ExecutionException) {
        throw RuntimeException(e)
      }
    }
  }

  companion object {
    private fun createMavenWrapper(pomPath: Path, context: String) {
      val fileName = pomPath.parent.resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties")
      fileName.write(context)
    }
  }
}
