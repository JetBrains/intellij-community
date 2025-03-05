// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProject
import org.junit.Test

class RepositoriesImportingMavenCentralTest : MavenMultiVersionImportingTestCase() {
  private val mavenProject: MavenProject
    get() = projectsTree.rootProjects[0]

  override fun setUp() {
    super.setUp()
    updateSettingsXml("")
  }

  @Test
  fun importSimpleProject() = runBlocking {
    importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      """.trimIndent())
    //val mavenProject = projectsManager.findProject(projectPom)
    assertNotNull(mavenProject)
    assertSameElements(mavenProject.remoteRepositories.map { it.url }, "https://repo.maven.apache.org/maven2")
  }

  @Test
  fun testOverridingCentralRepository() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <repositories>
                      <repository>
                        <id>central</id>
                        <url>https://my.repository.com</url>
                      </repository>
                    </repositories>
                    """.trimIndent())

    val result = mavenProject.remoteRepositories
    assertEquals(1, result.size)
    assertEquals("central", result[0].id)
    assertEquals("https://my.repository.com", result[0].url)
  }

  @Test
  fun testCollectingRepositories() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <repositories>
                      <repository>
                        <id>one</id>
                        <url>https://repository.one.com</url>
                      </repository>
                      <repository>
                        <id>two</id>
                        <url>https://repository.two.com</url>
                      </repository>
                    </repositories>
                    """.trimIndent())

    val result = mavenProject.remoteRepositories
    assertEquals(3, result.size)
    assertEquals("one", result[0].id)
    assertEquals("two", result[1].id)
    assertEquals("central", result[2].id)
  }

  @Test
  fun testCollectingRepositoriesFromParent() = runBlocking {
    val m1 = createModulePom("p1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>p1</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <repositories>
                                         <repository>
                                           <id>one</id>
                                           <url>https://repository.one.com</url>
                                         </repository>
                                         <repository>
                                           <id>two</id>
                                           <url>https://repository.two.com</url>
                                         </repository>
                                       </repositories>
                                       """.trimIndent())

    val m2 = createModulePom("p2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>p2</artifactId>
                                       <version>1</version>
                                       <parent>
                                         <groupId>test</groupId>
                                         <artifactId>p1</artifactId>
                                         <version>1</version>
                                       </parent>
                                       """.trimIndent())

    importProjectsAsync(m1, m2)

    var result = projectsTree.rootProjects[0].remoteRepositories
    assertEquals(3, result.size)
    assertEquals("one", result[0].id)
    assertEquals("two", result[1].id)
    assertEquals("central", result[2].id)

    result = projectsTree.rootProjects[1].remoteRepositories
    assertEquals(3, result.size)
    assertEquals("one", result[0].id)
    assertEquals("two", result[1].id)
    assertEquals("central", result[2].id)
  }
}