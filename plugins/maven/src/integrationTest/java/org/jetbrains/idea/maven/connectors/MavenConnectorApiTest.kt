// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.connectors

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeVersionMoreThan
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.openapi.components.service
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenLogEventHandler
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenWorkspaceMap
import org.jetbrains.idea.maven.project.MavenEmbedderWrappersManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.Properties

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenConnectorApiTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  


  @BeforeEach
  fun setUp() {
    maven.projectsManager.initForTests()
  }

  @Test
  fun testResolveProjectsWithProfiles() = runBlocking {
    maven.assumeVersionMoreThan("3.1.0")
    val mavenProject = maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    val m1 = maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <profiles>
        <profile>
          <id>test</id>
          <dependencies>
            <dependency>
              <groupId>junit</groupId>
              <artifactId>junit</artifactId>
              <version>4.0</version>
            </dependency>
          </dependencies>
        </profile>
      </profiles>
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m1</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())
    val mavenEmbedderWrappers = maven.project.service<MavenEmbedderWrappersManager>().createMavenEmbedderWrappers()
    val embedder = mavenEmbedderWrappers.getEmbedder(maven.projectPath)
    val map = MavenWorkspaceMap()
    map.register(MavenId("test:m1:1"), m1.toNioPath().toFile())
    map.register(MavenId("test:m2:1"), m2.toNioPath().toFile())
    map.register(MavenId("test:project:1"), mavenProject.toNioPath().toFile())
    val executionResults = embedder.resolveProject(listOf(mavenProject),
                                                   mapOf(mavenProject to null, m1 to null, m2 to null),
                                                   mapOf(),
                                                   MavenExplicitProfiles(listOf("test"), emptyList()),
                                                   MockReporter(),
                                                   MavenLogEventHandler,
                                                   map,
                                                   false,
                                                   Properties())

    assertEquals(3, executionResults.size)
    val results = executionResults.associateBy { it.projectData!!.mavenModel.mavenId.toString() }
    val depsOfM1 = results["test:m1:1"]!!.projectData!!.mavenModel.dependencies
    assertOrderedEquals(depsOfM1.map { it.mavenId.toString() }, "junit:junit:4.0")

    val depsOfM2 = results["test:m2:1"]!!.projectData!!.mavenModel.dependencies
    assertOrderedEquals(depsOfM2.map { it.mavenId.toString() }, "test:m1:1", "junit:junit:4.0")

  }
}

private class MockReporter : RawProgressReporter {
  val out = StringBuilder()
  override fun text(text: String?) {
    text?.let { out.append(it) }
  }
}