// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenEmbedderWrappersManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.MavenWrapper
import org.jetbrains.idea.maven.server.*
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider
import org.junit.Test
import java.nio.file.Files.createDirectories
import java.nio.file.Path.of

class MavenImportingConnectorsTest : MavenMultiVersionImportingTestCase() {

  private lateinit var myAnotherProjectRoot: VirtualFile

  override fun setUpInWriteAction() {
    super.setUpInWriteAction()
    val projectDir = createDirectories(of(dir.toString(), "anotherProject"))
    myAnotherProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir)!!
  }

  @Test
  fun testShouldNotCreateNewConnectorForNewProject() = runBlocking {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>""".trimIndent())
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>""".trimIndent())
    importProjectAsync()
    assertModules("project1", "m1")
    val p2Root = createPomFile(myAnotherProjectRoot, """
      <groupId>test</groupId>
      <artifactId>project2</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m2</module>
      </modules>""".trimIndent())
    createModulePom("../anotherProject/m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>""".trimIndent())
    runBlockingMaybeCancellable {
      MavenOpenProjectProvider().linkToExistingProjectAsync(p2Root, project)
    }
    assertModules("project1", "m1", "project2", "m2")
    val allConnectors = MavenServerManager.getInstance().getAllConnectors()
    assertEquals(1, allConnectors.size)

    assertUnorderedElementsAreEqual(
      allConnectors.first().multimoduleDirectories.map {
        getRelativePath(dir, it)
      },
      listOf("project", "anotherProject")
    )
  }

  @Test
  fun testShouldCreateNewConnectorForNewProjectIfJvmConfigPresents() = runBlocking {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>""".trimIndent())
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>""".trimIndent())
    importProjectAsync()
    assertModules("project1", "m1")
    val p2Root = createPomFile(myAnotherProjectRoot, """
      <groupId>test</groupId>
      <artifactId>project2</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m2</module>
      </modules>""".trimIndent())
    createModulePom("../anotherProject/m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>""".trimIndent())

    createProjectSubFile("../anotherProject/.mvn/jvm.config", "-Dsomething=blablabla")
    MavenOpenProjectProvider().linkToExistingProjectAsync(p2Root, project)
    assertModules("project1", "m1", "project2", "m2")

    assertEquals(2, MavenServerManager.getInstance().getAllConnectors().size)

    assertUnorderedElementsAreEqual(
      MavenServerManager.getInstance().getAllConnectors().map {
        getRelativePath(dir, it.multimoduleDirectories.first())
      },
      listOf("project", "anotherProject")
    )
  }

  @Test
  fun testShouldNotCreateNewConnectorForNewProjectIfJvmConfigPresentsAndRegistrySet() = runBlocking {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>""".trimIndent())
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>""".trimIndent())
    importProjectAsync()
    assertModules("project1", "m1")
    val p2Root = createPomFile(myAnotherProjectRoot, """
      <groupId>test</groupId>
      <artifactId>project2</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m2</module>
      </modules>""".trimIndent())
    createModulePom("../anotherProject/m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>""".trimIndent())

    createProjectSubFile("../anotherProject/.mvn/jvm.config", "-Dsomething=blablabla")
    val value = Registry.`is`("maven.server.per.idea.project")
    try {
      Registry.get("maven.server.per.idea.project").setValue(true)
      waitForImportWithinTimeout {
        MavenOpenProjectProvider().linkToExistingProjectAsync(p2Root, project)
      }
      assertModules("project1", "m1", "project2", "m2")

      assertEquals(1, MavenServerManager.getInstance().getAllConnectors().size)

      assertContainsElements(
        MavenServerManager.getInstance().getAllConnectors().first().multimoduleDirectories.map {
          getRelativePath(dir, it)
        },
        listOf("project", "anotherProject")
      )
    }
    finally {
      Registry.get("maven.server.per.idea.project").setValue(value)
    }
  }


  @Test
  fun testShouldNotCreateNewConnectorsIfProjectRootIsInSiblingDir() = runBlocking {
    projectPom = createModulePom("parent", """
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>../m1</module>
      </modules>""".trimIndent())
    createModulePom("m1", """
      <artifactId>m1</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project1</artifactId>
        <version>1</version>
        <relativePath>../parent/pom.xml</relativePath>
      </parent>""".trimIndent())
    importProjectAsync()
    assertModules("project1", mn("project", "m1"))

    assertEquals(1, MavenServerManager.getInstance().getAllConnectors().size)

    assertUnorderedElementsAreEqual(
      MavenServerManager.getInstance().getAllConnectors().first().multimoduleDirectories.map {
        getRelativePath(dir, it)
      }.map { it?.replace("\\", "/") },
      listOf("project/parent", "project/m1")
    )
  }

  @Test
  fun testCreateNewConnectorsVmOptionsMvnAndSettings() = runBlocking {
    projectPom = createModulePom("parent", """
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>""".trimIndent())
    val settingsComponent = MavenWorkspaceSettingsComponent.getInstance(project)
    settingsComponent.settings.importingSettings.vmOptionsForImporter = "-Dsomething=settings"
    createProjectSubFile(".mvn/jvm.config", "-Dsomething=jvm")
    importProjectAsync()
    val allConnectors = MavenServerManager.getInstance().getAllConnectors()
    assertEquals(1, allConnectors.size)
    val mavenServerConnector = allConnectors.elementAt(0)
    assertEquals("-Dsomething=settings", mavenServerConnector.vmOptions)
  }

  @Test
  fun testCreateNewConnectorsVmOptionsMvn() = runBlocking {
    projectPom = createModulePom("parent", """
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>""".trimIndent())
    createProjectSubFile(".mvn/jvm.config", "-Dsomething=something")
    importProjectAsync()
    val allConnectors = MavenServerManager.getInstance().getAllConnectors()
    assertEquals(1, allConnectors.size)
    val mavenServerConnector = allConnectors.elementAt(0)
    assertEquals("-Dsomething=something", mavenServerConnector.vmOptions)
  }

  @Test
  fun testCreateNewConnectorsVmOptionsSettings() = runBlocking {
    projectPom = createModulePom("parent", """
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>""".trimIndent())
    val settingsComponent = MavenWorkspaceSettingsComponent.getInstance(project)
    settingsComponent.settings.importingSettings.vmOptionsForImporter = "-Dsomething=settings"
    importProjectAsync()
    val allConnectors = MavenServerManager.getInstance().getAllConnectors()
    assertEquals(1, allConnectors.size)
    val mavenServerConnector = allConnectors.elementAt(0)
    assertEquals("-Dsomething=settings", mavenServerConnector.vmOptions)
  }

  @Test
  fun testCreateNewConnectorsVmOptionsJvmXms() = runBlocking {
    projectPom = createModulePom("parent", """
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>
      <packaging>pom</packaging>""".trimIndent())
    createProjectSubFile(".mvn/jvm.config", "-Xms800m")
    importProjectAsync()
    assertEquals(1, MavenServerManager.getInstance().getAllConnectors().size)
  }

  @Test
  fun testShouldPassValidConfigurationOfGlobalSettingsToConnector() = runBlocking {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project1</artifactId>
      <version>1</version>""".trimIndent())
    createProjectSubFile(".mvn/wrapper/maven-wrapper.properties",
                         "distributionUrl=" + MavenDistributionsCache.resolveEmbeddedMavenHome().mavenHome.toUri().toASCIIString())
    val settingsRef = Ref<MavenEmbedderSettings>()
    ApplicationManager.getApplication().replaceService(MavenServerManager.MavenServerConnectorFactory::class.java,
                                                       object : MavenServerManager.MavenServerConnectorFactory {
                                                         override fun create(project: Project,
                                                                             jdk: Sdk,
                                                                             vmOptions: String,
                                                                             debugPort: Int?,
                                                                             mavenDistribution: MavenDistribution,
                                                                             multimoduleDirectory: String): MavenServerConnector {
                                                           return object : MavenServerConnectorImpl(project, jdk, vmOptions, null,
                                                                                                    mavenDistribution,
                                                                                                    multimoduleDirectory) {
                                                             override suspend fun createEmbedder(settings: MavenEmbedderSettings): MavenServerEmbedder {
                                                               settingsRef.set(settings)
                                                               throw UnsupportedOperationException()
                                                             }
                                                           }
                                                         }

                                                       }, testRootDisposable)
    MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings.mavenHomeType = MavenWrapper
    assertThrows(UnsupportedOperationException::class.java) {
      runBlockingMaybeCancellable {
        val mavenEmbedderWrappers = project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
        mavenEmbedderWrappers.getEmbedder(projectRoot.path).getEmbedder()
      }
    }
    assertNotNull(settingsRef.get())
    val path = MavenServerManager.getInstance().getConnector(project, projectRoot.path).mavenDistribution.mavenHome

    assertEquals(path.resolve("conf/settings.xml").toString(), settingsRef.get().settings.globalSettingsPath)
  }


}