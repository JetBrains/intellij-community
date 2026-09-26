// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.getActualMavenVersion
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class Maven4SpecificImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp() {
    maven.assumeModel_4_1_0("Tests are specific for maven 4.1.0 only")
  }

  @Test
  fun testChildAutoscanningWorksForMaven4() = runBlocking {
    val modulePom = maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
    """.trimIndent())
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      """)
    maven.assertModules("project", "m1")
    val moduleProject = maven.projectsManager.findProject(modulePom)
    val rootProject = maven.projectsManager.findProject(maven.projectPom)
    assertNotNull(moduleProject, "m1 project should not be null")
    assertNotNull(rootProject, "root project should not be null")
    assertSameElements("Project root should have one child", maven.projectsManager.projectsTree.getModules(rootProject!!), listOf(moduleProject))
    assertSame(maven.projectsManager.projectsTree.findRootProject(moduleProject!!), rootProject, "Root projecy should be same")
  }

  @Test
  fun testChildAutoscanningCouldBeDisabledForMaven4() = runBlocking {
    val version = maven.getActualMavenVersion()
    Assumptions.assumeTrue(false, "For maven newer that 4.0.0-rc4 - see IDEA-379195 and https://github.com/apache/maven/issues/11114")
    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
    """.trimIndent())
    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <subprojects/>
      """)
    maven.assertModules("project")
  }

  @Test
  fun testGrandChildAutoscanningDoesNotWorksForMaven4() = runBlocking {
     maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
    """.trimIndent())

    maven.createModulePom("sub/m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
    """.trimIndent())

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      """)
    maven.assertModules("project", "m1")

  }

  @Test
  fun testAutomaticParentVersioning() = runBlocking {
    val modulePom = maven.createModulePom("m1", """
      <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
      </parent>
      <artifactId>m1</artifactId>
     
    """.trimIndent())

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>42</version>
      <packaging>pom</packaging>
      """)
    maven.assertModules("project", "m1")
    val moduleProject = maven.projectsManager.findProject(modulePom)
    assertNotNull(moduleProject)
    assertEquals("42", moduleProject!!.mavenId.version, "Should have parent");
    val rootProject = maven.projectsManager.findProject(maven.projectPom)
    assertNotNull(moduleProject, "m1 project should not be null")
    assertNotNull(rootProject, "root project should not be null")
    assertSameElements("Project root should have one child", maven.projectsManager.projectsTree.getModules(rootProject!!), listOf(moduleProject))
    assertSame(maven.projectsManager.projectsTree.findRootProject(moduleProject!!), rootProject, "Root projecy should be same")
  }
}