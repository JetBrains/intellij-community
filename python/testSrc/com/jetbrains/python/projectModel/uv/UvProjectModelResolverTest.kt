// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.copyRecursively
import com.jetbrains.python.PythonTestUtil.getTestDataPath
import com.jetbrains.python.projectModel.ExternalProjectGraph
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertNotNull
import java.nio.file.Path
import java.nio.file.Paths.get
import kotlin.io.path.div

@TestApplication
class UvProjectModelResolverTest {
  private val testRoot by tempPathFixture()

  private lateinit var testInfo: TestInfo

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    this.testInfo = testInfo
  }

  @Test
  fun nestedWorkspaces() {
    val ijProjectRoot = testRoot / "project"
    testDataDir.copyRecursively(ijProjectRoot)
    val graph = UvProjectModelResolver.discoverProjectRootSubgraph(ijProjectRoot / "root-ws")
    assertNotNull(graph)
    assertEquals(4, graph.projects.size)

    val rootWs = graph.project("root-ws")
    assertNotNull(rootWs)
    assertTrue(rootWs.isWorkspace)
    assertEquals(ijProjectRoot / "root-ws", rootWs.root)

    val rootWsMember = graph.project("root-ws-member")
    assertNotNull(rootWsMember)
    assertFalse(rootWsMember.isWorkspace)
    assertEquals(ijProjectRoot / "root-ws" / "root-ws-member", rootWsMember.root)
    assertEquals(rootWs, rootWsMember.parentWorkspace)

    val nestedWs = graph.project("nested-ws")
    assertNotNull(nestedWs)
    assertTrue(nestedWs.isWorkspace)
    assertEquals(ijProjectRoot / "root-ws" / "nested-ws", nestedWs.root)

    val nestedWsMember = graph.project("nested-ws-member")
    assertNotNull(nestedWsMember)
    assertFalse(nestedWsMember.isWorkspace)
    assertEquals(ijProjectRoot / "root-ws" / "nested-ws" / "nested-ws-member", nestedWsMember.root)
    assertEquals(nestedWs, nestedWsMember.parentWorkspace)
  }

  @Test
  fun workspaceInsideStandalone() {
    val ijProjectRoot = testRoot / "project"
    testDataDir.copyRecursively(ijProjectRoot)
    val graph = UvProjectModelResolver.discoverProjectRootSubgraph(ijProjectRoot / "project")

    assertNotNull(graph)
    assertEquals(3, graph.projects.size)

    val standaloneProject = graph.project("project")
    assertNotNull(standaloneProject)
    assertFalse(standaloneProject.isWorkspace)
    assertEquals(ijProjectRoot / "project" , standaloneProject.root)

    val wsRoot = graph.project("ws-root")
    assertNotNull(wsRoot)
    assertTrue(wsRoot.isWorkspace)
    assertEquals(ijProjectRoot / "project" / "ws-root", wsRoot.root)

    val wsMember = graph.project("ws-member")
    assertNotNull(wsMember)
    assertFalse(wsMember.isWorkspace)
    assertEquals(ijProjectRoot / "project" / "ws-root" / "ws-member", wsMember.root)
    assertEquals(wsRoot, wsMember.parentWorkspace)
  }

  @Test
  fun intermediateNonWorkspaceProjects() {
    val ijProjectRoot = testRoot / "project"
    testDataDir.copyRecursively(ijProjectRoot)
    val graph = UvProjectModelResolver.discoverProjectRootSubgraph(ijProjectRoot / "root-ws")

    assertNotNull(graph)
    assertEquals(4, graph.projects.size)

    val wsRoot = graph.project("root-ws")
    assertNotNull(wsRoot)
    assertTrue(wsRoot.isWorkspace)
    assertEquals(ijProjectRoot / "root-ws", wsRoot.root)

    val intermediateNonWsProject = graph.project("intermediate-non-ws")
    assertNotNull(intermediateNonWsProject)
    assertFalse(intermediateNonWsProject.isWorkspace)
    assertNull(intermediateNonWsProject.parentWorkspace)
    assertEquals(ijProjectRoot / "root-ws" / "intermediate-non-ws", intermediateNonWsProject.root)

    val nestedWsMember = graph.project("root-ws-member")
    assertNotNull(nestedWsMember)
    assertFalse(nestedWsMember.isWorkspace)
    assertEquals(wsRoot, nestedWsMember.parentWorkspace)
    assertEquals(ijProjectRoot / "root-ws" / "intermediate-non-ws" / "root-ws-member", nestedWsMember.root)

    val directWsMember = graph.project("root-ws-direct-member")
    assertNotNull(directWsMember)
    assertFalse(directWsMember.isWorkspace)
    assertEquals(wsRoot, directWsMember.parentWorkspace)
    assertEquals(ijProjectRoot / "root-ws" / "root-ws-direct-member", directWsMember.root)
  }

  @Test
  fun intermediateNonProjectDirs() {
    val ijProjectRoot = testRoot / "project"
    testDataDir.copyRecursively(ijProjectRoot)
    val graph = UvProjectModelResolver.discoverProjectRootSubgraph(ijProjectRoot / "ws-root")

    assertNotNull(graph)
    assertEquals(2, graph.projects.size)

    val wsRoot = graph.project("ws-root")
    assertNotNull(wsRoot)
    assertTrue(wsRoot.isWorkspace)
    assertEquals(ijProjectRoot / "ws-root", wsRoot.root)

    val wsMember = graph.project("root-ws-member")
    assertNotNull(wsMember)
    assertFalse(wsMember.isWorkspace)
    assertEquals(wsRoot, wsMember.parentWorkspace)
    assertEquals(ijProjectRoot / "ws-root" / "dir" / "subdir" / "root-ws-member", wsMember.root)
  }


  private val testDataDir: Path
    get() = get(getTestDataPath()) / "projectModel" / testInfo.testMethod.get().name

  private fun ExternalProjectGraph<UvProject>.project(name: String): UvProject? = projects.firstOrNull { it.name == name }
}
