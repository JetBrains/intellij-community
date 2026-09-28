// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.maven.testFramework.fixtures.createMavenWrapper
import com.intellij.maven.testFramework.fixtures.createPom
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.maven.testFramework.fixtures.importModuleFrom
import com.intellij.maven.testFramework.fixtures.importProjectFrom
import com.intellij.maven.testFramework.fixtures.mavenProjectWizardFixture
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.write
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.jetbrains.idea.maven.project.BundledMaven3
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
class MavenImportWizardTest {
  private val maven by mavenProjectWizardFixture()

  @Test
  fun testImportModule() = runBlocking {
    val pom = maven.createPom()
    maven.importModuleFrom(pom)
    Unit
  }

  @Test
  fun testImportProject() = runBlocking {
    val pom = maven.createPom()
    val module = maven.importProjectFrom(pom)

    val settings = MavenWorkspaceSettingsComponent.getInstance(module.project).settings.generalSettings
    val mavenHome = settings.mavenHomeType
    assertSame(BundledMaven3, mavenHome)
    assertTrue(MavenProjectsNavigator.getInstance(module.project).groupModules)
    assertTrue(settings.isUseMavenConfig)
  }

  @Test
  fun testImportProjectWithWrapper() = runBlocking {
    val pom = maven.createPom()
    maven.createMavenWrapper(pom,
                             "distributionUrl=https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.1/apache-maven-3.8.1-bin.zip")
    val module = maven.importProjectFrom(pom)
    assertWithinTimeout {
      val mavenHome = MavenWorkspaceSettingsComponent.getInstance(module.project).settings.generalSettings.mavenHomeType
      assertSame(MavenWrapper, mavenHome)
    }
  }

  @Test
  fun testImportProjectWithWrapperWithoutUrl() = runBlocking {
    val pom = maven.createPom()
    maven.createMavenWrapper(pom, "property1=value1")
    val module = maven.importProjectFrom(pom)
    val mavenHome = MavenWorkspaceSettingsComponent.getInstance(module.project).settings.generalSettings.mavenHomeType
    assertSame(BundledMaven3, mavenHome)
  }

  @Test
  fun testImportProjectWithManyPoms() = runBlocking {
    val pom1 = maven.createPom("pom1.xml")
    val pom2 = pom1.parent.resolve("pom2.xml")
    pom2.write(createPomXml(
      MavenConstants.MODEL_VERSION_4_0_0,
      """
      <groupId>test</groupId>
      <artifactId>project2</artifactId>
      <version>1</version>
      """.trimIndent(),
      omitModelVersionTag = false))
    val module = maven.importProjectFrom(pom1)
    val project = module.project
    assertWithinTimeout {
      val modules = ModuleManager.getInstance(project).modules
      val moduleNames = HashSet<String>()
      for (existingModule in modules) {
        moduleNames.add(existingModule.name)
      }
      assertEquals(setOf("project", "project2"), moduleNames)
    }
    val projectsManager = MavenProjectsManager.getInstance(project)
    val mavenProjectNames = HashSet<String?>()
    for (p in projectsManager.projects) {
      mavenProjectNames.add(p.mavenId.artifactId)
    }
    assertEquals(setOf("project", "project2"), mavenProjectNames)
  }

  @Test
  fun testShouldStoreImlFileInSameDirAsPomXml() = runBlocking {
    val dir = Files.createTempDirectory(maven.wizards.contentRoot, "project")
    val projectName = dir.fileName.toString()
    val pom = dir.resolve("pom.xml")
    pom.write(createPomXml(
      MavenConstants.MODEL_VERSION_4_0_0,
      """
      <groupId>test</groupId>
      <artifactId>
      $projectName</artifactId>
      <version>1</version>
      """.trimIndent(),
      omitModelVersionTag = false))
    val module = maven.importProjectFrom(pom)
    val project = module.project
    ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
    val modules = ModuleManager.getInstance(project).modules
    val imlFile = dir.resolve("$projectName.iml").toFile()
    val m = modules.filter {
      FileUtil.toSystemIndependentName(it.moduleFilePath) == FileUtil.toSystemIndependentName(imlFile.absolutePath)
    }
    assertEquals(1, m.size)
  }
}
