// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.project.importing.MavenImportingManager.Companion.getInstance
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider
import org.junit.Test
import java.io.File

class MavenImportingConnectorsTest : MavenMultiVersionImportingTestCase() {
  protected lateinit var myAnotherProjectRoot: VirtualFile

  @Throws(Exception::class)
  override fun setUpInWriteAction() {
    super.setUpInWriteAction()
    val projectDir = File(myDir, "anotherProject")
    projectDir.mkdirs()
    myAnotherProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir)!!
  }

  @Test
  fun testShouldNotCreateNewConnectorForNewProject() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project1</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "<module>m1</module>" +
                     " </modules>")
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>")
    importProject()
    assertModules("project1", "m1")
    val p2Root = createPomFile(myAnotherProjectRoot, "<groupId>test</groupId>" +
                                                     "<artifactId>project2</artifactId>" +
                                                     "<version>1</version>" +
                                                     "<packaging>pom</packaging>" +
                                                     "<modules>" +
                                                     "<module>m2</module>" +
                                                     " </modules>")
    createModulePom("../anotherProject/m2", "<groupId>test</groupId>" +
                                            "<artifactId>m2</artifactId>" +
                                            "<version>2</version>")
    MavenOpenProjectProvider().linkToExistingProject(p2Root, myProject)
    waitForLinkingCompleted();
    assertModules("project1", "m1", "project2", "m2")
    assertEquals(1, MavenServerManager.getInstance().allConnectors.size);

    assertUnorderedElementsAreEqual(
      MavenServerManager.getInstance().allConnectors.first().multimoduleDirectories.map {
        FileUtil.getRelativePath(myDir, File(it))
      },
      listOf("project", "anotherProject")
    )
  }

  private fun waitForLinkingCompleted() {
    if (!isNewImportingProcess) return;
    PlatformTestUtil.waitForPromise(getInstance(myProject).getImportFinishPromise())
  }

  @Test
  fun testShouldCreateNewConnectorForNewProjectIfJvmConfigPresents() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project1</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "<module>m1</module>" +
                     " </modules>")
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>")
    importProject()
    assertModules("project1", "m1")
    val p2Root = createPomFile(myAnotherProjectRoot, "<groupId>test</groupId>" +
                                                     "<artifactId>project2</artifactId>" +
                                                     "<version>1</version>" +
                                                     "<packaging>pom</packaging>" +
                                                     "<modules>" +
                                                     "<module>m2</module>" +
                                                     " </modules>")
    createModulePom("../anotherProject/m2", "<groupId>test</groupId>" +
                                            "<artifactId>m2</artifactId>" +
                                            "<version>2</version>")

    createProjectSubFile("../anotherProject/.mvn/jvm.config", "-Dsomething=blablabla")
    MavenOpenProjectProvider().linkToExistingProject(p2Root, myProject)
    waitForLinkingCompleted();
    assertModules("project1", "m1", "project2", "m2")

    assertEquals(2, MavenServerManager.getInstance().allConnectors.size);

    assertUnorderedElementsAreEqual(
      MavenServerManager.getInstance().allConnectors.map {
        FileUtil.getRelativePath(myDir, File(it.multimoduleDirectories.first()))
      },
      listOf("project", "anotherProject")
    )
  }

  @Test
  fun testShouldNotCreateNewConnectorForNewProjectIfJvmConfigPresentsAndRegistrySet() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project1</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "<module>m1</module>" +
                     " </modules>")
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>")
    importProject()
    assertModules("project1", "m1")
    val p2Root = createPomFile(myAnotherProjectRoot, "<groupId>test</groupId>" +
                                                     "<artifactId>project2</artifactId>" +
                                                     "<version>1</version>" +
                                                     "<packaging>pom</packaging>" +
                                                     "<modules>" +
                                                     "<module>m2</module>" +
                                                     " </modules>")
    createModulePom("../anotherProject/m2", "<groupId>test</groupId>" +
                                            "<artifactId>m2</artifactId>" +
                                            "<version>2</version>")

    createProjectSubFile("../anotherProject/.mvn/jvm.config", "-Dsomething=blablabla")
    val value = Registry.`is`("maven.server.per.idea.project")
    try {
      Registry.get("maven.server.per.idea.project").setValue(true);
      MavenOpenProjectProvider().linkToExistingProject(p2Root, myProject)
      waitForLinkingCompleted();
      assertModules("project1", "m1", "project2", "m2")

      assertEquals(1, MavenServerManager.getInstance().allConnectors.size);

      assertUnorderedElementsAreEqual(
        MavenServerManager.getInstance().allConnectors.first().multimoduleDirectories.map {
          FileUtil.getRelativePath(myDir, File(it))
        },
        listOf("project", "anotherProject")
      )
    }
    finally {
      Registry.get("maven.server.per.idea.project").setValue(value)
    }

  }


  @Test
  fun testShouldNotCreateNewConnectorsIfProjectRootIsInSiblingDir() {
    myProjectPom = createModulePom("parent", "<groupId>test</groupId>" +
                                             "<artifactId>project1</artifactId>" +
                                             "<version>1</version>" +
                                             "<packaging>pom</packaging>" +
                                             "<modules>" +
                                             "<module>../m1</module>" +
                                             " </modules>")
    createModulePom("m1", "<parent>\n" +
                          "    <groupId>test</groupId>\n" +
                          "    <artifactId>project1</artifactId>\n" +
                          "    <version>1</version>\n" +
                          "    <relativePath>../parent/pom.xml</relativePath>\n" +
                          "  </parent>")
    importProject()
    assertModules("project1", "m1")

    assertEquals(1, MavenServerManager.getInstance().allConnectors.size);

    assertUnorderedElementsAreEqual(
      MavenServerManager.getInstance().allConnectors.first().multimoduleDirectories.map {
        FileUtil.getRelativePath(myDir, File(it))
      }.map { it?.replace("\\", "/") },
      listOf("project/parent", "project/m1")
    )
  }

  @Test
  fun testCreateNewConnectorsVmOptionsMvnAndSettings() {
    myProjectPom = createModulePom("parent", "<groupId>test</groupId>" +
                                             "<artifactId>project1</artifactId>" +
                                             "<version>1</version>" +
                                             "<packaging>pom</packaging>"
    )
    val settingsComponent = MavenWorkspaceSettingsComponent.getInstance(myProject)
    settingsComponent.getSettings().getImportingSettings().setVmOptionsForImporter("-Dsomething=settings");
    createProjectSubFile(".mvn/jvm.config", "-Dsomething=jvm")
    importProject()
    val allConnectors = MavenServerManager.getInstance().allConnectors
    assertEquals(1, allConnectors.size);
    val mavenServerConnector = allConnectors.elementAt(0)
    assertEquals("-Dsomething=settings", mavenServerConnector.vmOptions)
  }

  @Test
  fun testCreateNewConnectorsVmOptionsMvn() {
    myProjectPom = createModulePom("parent", "<groupId>test</groupId>" +
                                             "<artifactId>project1</artifactId>" +
                                             "<version>1</version>" +
                                             "<packaging>pom</packaging>"
    )
    createProjectSubFile(".mvn/jvm.config", "-Dsomething=something")
    importProject()
    val allConnectors = MavenServerManager.getInstance().allConnectors
    assertEquals(1, allConnectors.size);
    val mavenServerConnector = allConnectors.elementAt(0)
    assertEquals("-Dsomething=something", mavenServerConnector.vmOptions)
  }

  @Test
  fun testCreateNewConnectorsVmOptionsSettings() {
    myProjectPom = createModulePom("parent", "<groupId>test</groupId>" +
                                             "<artifactId>project1</artifactId>" +
                                             "<version>1</version>" +
                                             "<packaging>pom</packaging>"
    )
    val settingsComponent = MavenWorkspaceSettingsComponent.getInstance(myProject)
    settingsComponent.getSettings().getImportingSettings().setVmOptionsForImporter("-Dsomething=settings");
    importProject()
    val allConnectors = MavenServerManager.getInstance().allConnectors
    assertEquals(1, allConnectors.size);
    val mavenServerConnector = allConnectors.elementAt(0)
    assertEquals("-Dsomething=settings", mavenServerConnector.vmOptions)
  }

  @Test
  fun testCreateNewConnectorsVmOptionsJvmXms() {
    myProjectPom = createModulePom("parent", "<groupId>test</groupId>" +
                                             "<artifactId>project1</artifactId>" +
                                             "<version>1</version>" +
                                             "<packaging>pom</packaging>"
    )
    createProjectSubFile(".mvn/jvm.config", "-Xms800m")
    importProject()
    assertEquals(1, MavenServerManager.getInstance().allConnectors.size);
  }


}