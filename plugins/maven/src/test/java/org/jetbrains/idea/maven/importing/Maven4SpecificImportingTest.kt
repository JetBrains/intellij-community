// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test

class Maven4SpecificImportingTest : MavenMultiVersionImportingTestCase() {
  override fun setUp() {
    super.setUp()
    assumeModel_4_1_0("Tests are specific for maven 4.1.0 only")
  }

  @Test
  fun testChildAutoscanningWorksForMaven4() = runBlocking {
    val modulePom = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
    """.trimIndent())
    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      """)
    assertModules("project", "m1")
    val moduleProject = projectsManager.findProject(modulePom)
    val rootProject = projectsManager.findProject(projectPom)
    assertNotNull("m1 project should not be null", moduleProject)
    assertNotNull("root project should not be null", rootProject)
    assertSameElements("Project root should have one child", projectsManager.projectsTree.getModules(rootProject!!), listOf(moduleProject))
    assertSame("Root projecy should be same", projectsManager.projectsTree.findRootProject(moduleProject!!), rootProject)
  }

  @Test
  fun testChildAutoscanningCouldBeDisabledForMaven4() = runBlocking {
    val version = getActualMavenVersion()
    Assume.assumeTrue("For maven newer that 4.0.0-rc4 - see IDEA-379195 and https://github.com/apache/maven/issues/11114", false)
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
    """.trimIndent())
    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <subprojects/>
      """)
    assertModules("project")
  }

  @Test
  fun testGrandChildAutoscanningDoesNotWorksForMaven4() = runBlocking {
     createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
    """.trimIndent())

    createModulePom("sub/m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
    """.trimIndent())

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      """)
    assertModules("project", "m1")

  }

  @Test
  fun testAutomaticParentVersioning() = runBlocking {
    val modulePom = createModulePom("m1", """
      <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
      </parent>
      <artifactId>m1</artifactId>
     
    """.trimIndent())

    importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>42</version>
      <packaging>pom</packaging>
      """)
    assertModules("project", "m1")
    val moduleProject = projectsManager.findProject(modulePom)
    assertNotNull(moduleProject)
    assertEquals("Should have parent", "42", moduleProject!!.mavenId.version);
    val rootProject = projectsManager.findProject(projectPom)
    assertNotNull("m1 project should not be null", moduleProject)
    assertNotNull("root project should not be null", rootProject)
    assertSameElements("Project root should have one child", projectsManager.projectsTree.getModules(rootProject!!), listOf(moduleProject))
    assertSame("Root projecy should be same", projectsManager.projectsTree.findRootProject(moduleProject!!), rootProject)
  }
}