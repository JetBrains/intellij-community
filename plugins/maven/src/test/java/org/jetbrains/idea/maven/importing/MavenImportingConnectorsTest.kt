// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.MavenImportingTestCase
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.wizards.MavenOpenProjectProvider
import java.io.File

class MavenImportingConnectorsTest : MavenImportingTestCase() {
  protected lateinit var myAnotherProjectRoot: VirtualFile

  @Throws(Exception::class)
  override fun setUpInWriteAction() {
    super.setUpInWriteAction()
    val projectDir = File(myDir, "anotherProject")
    projectDir.mkdirs()
    myAnotherProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir)!!
  }

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
    assertModules("project1", "m1", "project2", "m2")
    assertEquals(1, MavenServerManager.getInstance().allConnectors.size);

    assertUnorderedElementsAreEqual(
      MavenServerManager.getInstance().allConnectors.first().multimoduleDirectories.map {
        FileUtil.getRelativePath(myDir, File(it))
      },
      listOf("project", "anotherProject")
    )
  }

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
    assertModules("project1", "m1", "project2", "m2")

    assertEquals(2, MavenServerManager.getInstance().allConnectors.size);

    assertUnorderedElementsAreEqual(
      MavenServerManager.getInstance().allConnectors.map {
        FileUtil.getRelativePath(myDir, File(it.multimoduleDirectories.first()))
      },
      listOf("project", "anotherProject")
    )
  }

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


}