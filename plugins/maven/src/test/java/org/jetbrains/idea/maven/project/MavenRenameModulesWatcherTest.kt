// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.openapi.project.ModuleListener
import org.junit.Test

class MavenRenameModulesWatcherTest : MavenDomTestCase() {
  override fun setUp() {
    super.setUp()
    myProjectsManager.initForTests()
    myProjectsManager.listenForExternalChanges()
  }

  private fun renameModule(oldName: String, newName: String) {
    val moduleManager = ModuleManager.getInstance(myProject)
    val module = moduleManager.findModuleByName(oldName)!!
    val modifiableModel = moduleManager.getModifiableModel()
    try {
      modifiableModel.renameModule(module, newName)
    }
    catch (e: ModuleWithNameAlreadyExists) {
      throw RuntimeException(e)
    }
    CommandProcessor.getInstance().executeCommand(myProject, {
      ApplicationManager.getApplication().runWriteAction {
        modifiableModel.commit()
        myProject.getMessageBus().syncPublisher(ModuleListener.TOPIC).modulesRenamed(myProject, listOf(module)) { oldName }
      }
    }, "renaming model", null)
  }

  @Test
  fun testModuleRenameArtifactIdChanged() {
    importProject("""
                  <groupId>group</groupId>
                  <artifactId>module</artifactId>
                  <version>1</version>
                  """.trimIndent())
    val oldModuleName = "module"
    val newModuleName = "newModule"
    renameModule(oldModuleName, newModuleName)
    val tag = findTag("project.artifactId")
    assertEquals(newModuleName, tag.getValue().getText())
  }

  @Test
  fun testModuleRenameImplicitGroupIdArtifactIdChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    importProject()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    renameModule(oldModuleName, newModuleName)
    val tag = findTag(m1File, "project.artifactId")
    assertEquals(newModuleName, tag.getValue().getText())
  }

  @Test
  fun testModuleRenameParentChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    importProject()
    val oldModuleName = "parent"
    val newModuleName = "newParent"
    renameModule(oldModuleName, newModuleName)
    val tag = findTag(m1File, "project.parent.artifactId")
    assertEquals(newModuleName, tag.getValue().getText())
  }

  @Test
  fun testModuleRenameDependenciesChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    val m2File = createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencies>
                    <dependency>
                      <version>1</version>
                      <groupId>group</groupId>
                      <artifactId>m1</artifactId>
                    </dependency>
                  </dependencies>
                  """.trimIndent())
    importProject()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    renameModule(oldModuleName, newModuleName)
    val tag = findTag(m2File, "project.dependencies.dependency.artifactId")
    assertEquals(newModuleName, tag.getValue().getText())
  }

  @Test
  fun testModuleRenameExclusionsChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <groupId>group</groupId>
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    val m2File = createModulePom("m2", """
                  <groupId>group</groupId>
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencies>
                    <dependency>
                      <version>1</version>
                      <groupId>group</groupId>
                      <artifactId>m1</artifactId>
                    </dependency>
                  </dependencies>
                  """.trimIndent())
    val m3File = createModulePom("m2", """
                  <groupId>group</groupId>
                  <artifactId>m3</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencies>
                    <dependency>
                      <version>1</version>
                      <groupId>group</groupId>
                      <artifactId>m2</artifactId>
                      <exclusions>
                        <exclusion>
                        <groupId>group</groupId>
                        <artifactId>m1</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                  """.trimIndent())
    importProject()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    renameModule(oldModuleName, newModuleName)
    val tag = findTag(m3File, "project.dependencies.dependency.exclusions.exclusion.artifactId")
    assertEquals(newModuleName, tag.getValue().getText())
  }

  @Test
  fun testModuleRenameAnotherGroupArtifactIdNotChanged() {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <groupId>group1</groupId>
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    val m2File = createModulePom("m2", """
                  <groupId>group</groupId>
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencies>
                    <dependency>
                      <version>1</version>
                      <groupId>anotherGroup</groupId>
                      <artifactId>m1</artifactId>
                    </dependency>
                  </dependencies>
                  """.trimIndent())
    importProject()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    renameModule(oldModuleName, newModuleName)
    val tag = findTag(m2File, "project.dependencies.dependency.artifactId")
    assertEquals(oldModuleName, tag.getValue().getText())
  }

  @Test
  fun test_when_ModuleMovedToGroup_then_ArtifactIdRemains() {
    importProject("""
                  <groupId>group</groupId>
                  <artifactId>module</artifactId>
                  <version>1</version>
                  """.trimIndent())
    val oldModuleName = "module"
    val newModuleName = "group.module"
    renameModule(oldModuleName, newModuleName)
    val tag = findTag("project.artifactId")
    assertEquals(oldModuleName, tag.getValue().getText())
  }
}
