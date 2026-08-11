// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import org.jetbrains.idea.maven.project.MavenProject
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.api.BeforeEach
import com.intellij.testFramework.UsefulTestCase.assertSameElements

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class RepositoriesImportingMavenCentralTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private val mavenProject: MavenProject
    get() = maven.projectsTree.rootProjects[0]

  @BeforeEach
  fun setUp() {
    runBlocking {
      maven.updateSettingsXml("")
    }

  }

  @Test
  fun importSimpleProject() = runBlocking {
    maven.importProjectAsync("""
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
    maven.importProjectAsync("""
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
    maven.importProjectAsync("""
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
    val m1 = maven.createModulePom("p1",
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

    val m2 = maven.createModulePom("p2",
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

    maven.importProjectsAsync(m1, m2)

    var result = maven.projectsTree.rootProjects[0].remoteRepositories
    assertEquals(3, result.size)
    assertEquals("one", result[0].id)
    assertEquals("two", result[1].id)
    assertEquals("central", result[2].id)

    result = maven.projectsTree.rootProjects[1].remoteRepositories
    assertEquals(3, result.size)
    assertEquals("one", result[0].id)
    assertEquals("two", result[1].id)
    assertEquals("central", result[2].id)
  }
}