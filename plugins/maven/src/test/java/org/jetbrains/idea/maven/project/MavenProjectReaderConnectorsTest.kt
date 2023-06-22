// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import org.jetbrains.idea.maven.server.*
import java.rmi.ConnectException

class MavenProjectReaderConnectorsTest : MavenProjectReaderTestCase() {
  fun `test when using stopped connector always then get exception`() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    assertThrows(ConnectException::class.java) {
      withStoppedConnector { readProject(myProjectPom).mavenId }
    }
  }

  fun `test when using stopped connector once then recover`() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())
    val p = withStoppedConnectorOnce { readProject(myProjectPom).mavenId }
    assertEquals("test", p.groupId)
    assertEquals("project", p.artifactId)
    assertEquals("1", p.version)
  }


  fun `test when connector is shut down then it is removed from manager`() {
    val mavenServerManager = MavenServerManager.getInstance()
    val connector1 = withCompatibleConnector { mavenServerManager.getConnector(myProject, myProject.basePath + "/1") }
    val connector2 = mavenServerManager.getConnector(myProject, myProject.basePath + "/2")
    assertTrue(connector1 === connector2)
    assertEquals(setOf(connector1), mavenServerManager.allConnectors.toSet())
    mavenServerManager.shutdownConnector(connector1, false)
    assertEquals(setOf<MavenServerConnector>(), mavenServerManager.allConnectors.toSet())
  }
}
