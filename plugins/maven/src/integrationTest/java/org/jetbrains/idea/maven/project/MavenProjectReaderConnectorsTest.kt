// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.evaluateEffectivePom
import org.jetbrains.idea.maven.fixtures.readProject
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.withCompatibleConnector
import org.jetbrains.idea.maven.server.withStoppedConnector
import org.jetbrains.idea.maven.server.withStoppedConnectorOnce
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.rmi.ConnectException

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectReaderConnectorsTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun `test when using stopped connector always then get exception`(): Unit = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    assertThrows(ConnectException::class.java) {
      runBlocking {
        withStoppedConnector { maven.evaluateEffectivePom(maven.projectPom) }
      }
    }
  }

  @Test
  fun `test when using stopped connector once then recover`() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    val p = withStoppedConnectorOnce { maven.readProject(maven.projectPom).mavenId }
    assertEquals("test", p.groupId)
    assertEquals("project", p.artifactId)
    assertEquals("1", p.version)
  }


  @Test
  fun `test when connector is shut down then it is removed from manager`() = runBlocking {
    val mavenServerManager = MavenServerManager.getInstance()
    val connector1 = withCompatibleConnector { mavenServerManager.getConnector(maven.project, maven.project.basePath + "/1") }
    val connector2 = mavenServerManager.getConnector(maven.project, maven.project.basePath + "/2")
    assertTrue(connector1 === connector2)
    assertEquals(setOf(connector1), mavenServerManager.getAllConnectors().toSet())
    mavenServerManager.shutdownConnector(connector1, false)
    assertEquals(setOf<MavenServerConnector>(), mavenServerManager.getAllConnectors().toSet())
  }
}
