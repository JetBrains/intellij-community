// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import org.jetbrains.idea.maven.fixtures.findTag
import org.jetbrains.idea.maven.fixtures.findTagValue
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import org.jetbrains.idea.maven.fixtures.renameModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenRenameModulesWatcherTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  
  @BeforeEach
  fun setUp() {
    maven.projectsManager.initForTests()
    maven.projectsManager.listenForExternalChanges()
  }

  @Test
  fun testModuleRenameArtifactIdChanged() = runBlocking {
    maven.importProjectAsync("""
                  <groupId>group</groupId>
                  <artifactId>module</artifactId>
                  <version>1</version>
                  """.trimIndent())
    val oldModuleName = "module"
    val newModuleName = "newModule"
    maven.renameModule(oldModuleName, newModuleName)
    val tag = maven.findTag("project.artifactId")
    readAction {
      assertEquals(newModuleName, tag.getValue().getText())
    }
  }

  @Test
  fun testModuleRenameImplicitGroupIdArtifactIdChanged() = runBlocking {
    maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    maven.renameModule(oldModuleName, newModuleName)
    val tag = maven.findTagValue(m1File, "project.artifactId")
    assertEquals(newModuleName, tag.getText())
  }

  @Test
  fun testModuleRenameParentChanged() = runBlocking {
    maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()
    val oldModuleName = "parent"
    val newModuleName = "newParent"
    maven.renameModule(oldModuleName, newModuleName)
    val tag = maven.findTagValue(m1File, "project.parent.artifactId")
    assertEquals(newModuleName, tag.getText())
  }

  @Test
  fun testModuleRenameDependenciesChanged() = runBlocking {
    maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    val m2File = maven.createModulePom("m2", """
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
    maven.importProjectAsync()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    maven.renameModule(oldModuleName, newModuleName)
    val tag = maven.findTagValue(m2File, "project.dependencies.dependency.artifactId")
    assertEquals(newModuleName, tag.getText())
  }

  @Test
  fun testModuleRenameDependencyManagementChanged() = runBlocking {
    maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    val m2File = maven.createModulePom("m2", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <version>1</version>
                        <groupId>group</groupId>
                        <artifactId>m1</artifactId>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  """.trimIndent())
    maven.importProjectAsync()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    maven.renameModule(oldModuleName, newModuleName)
    val tag = maven.findTagValue(m2File, "project.dependencyManagement.dependencies.dependency.artifactId")
    assertEquals(newModuleName, tag.getText())
  }

  @Test
  fun testModuleRenameExclusionsChanged() = runBlocking {
    maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <groupId>group</groupId>
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    val m2File = maven.createModulePom("m2", """
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
    val m3File = maven.createModulePom("m2", """
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
    maven.importProjectAsync()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    maven.renameModule(oldModuleName, newModuleName)
    val tag = maven.findTagValue(m3File, "project.dependencies.dependency.exclusions.exclusion.artifactId")
    assertEquals(newModuleName, tag.getText())
  }

  @Test
  fun testModuleRenameAnotherGroupArtifactIdNotChanged() = runBlocking {
    maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                    <module>m2</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <groupId>group1</groupId>
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <version>1</version>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    val m2File = maven.createModulePom("m2", """
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
    maven.importProjectAsync()
    val oldModuleName = "m1"
    val newModuleName = "m1new"
    maven.renameModule(oldModuleName, newModuleName)
    val tag = maven.findTagValue(m2File, "project.dependencies.dependency.artifactId")
    assertEquals(oldModuleName, tag.getText())
  }

  @Test
  fun test_when_ModuleMovedToGroup_then_ArtifactIdRemains() = runBlocking {
    maven.importProjectAsync("""
                  <groupId>group</groupId>
                  <artifactId>module</artifactId>
                  <version>1</version>
                  """.trimIndent())
    val oldModuleName = "module"
    val newModuleName = "group.module"
    maven.renameModule(oldModuleName, newModuleName)
    val tag = maven.findTag("project.artifactId")
    readAction {
      assertEquals(oldModuleName, tag.getValue().getText())
    }
  }
}
