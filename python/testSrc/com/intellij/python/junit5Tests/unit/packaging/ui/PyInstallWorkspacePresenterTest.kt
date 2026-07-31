// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.ui

import com.intellij.openapi.module.Module
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonWorkspaceSupport
import com.jetbrains.python.packaging.toolwindow.ui.PyInstallWorkspaceState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PyInstallWorkspacePresenterTest {

  private fun member(name: String) = PyWorkspaceMember(name)
  private fun group(name: String) = PyDependencyGroup(name)

  @Test
  fun `empty workspace with sdk module name synthesises a fallback member`() {
    val state = PyInstallWorkspaceState.compute(
      members = emptyList(),
      groupsByMember = mapOf(member("ignored") to listOf(group("dev"))),
      moduleMap = emptyMap(),
      sdkModuleName = "my-project",
      sdkModule = null,
      preselectModuleName = null,
    )

    val fallback = state.availableModules.single()
    assertEquals("my-project", fallback.name)
    assertEquals(fallback, state.selectedModule)
    assertNull(state.displayModuleName)
    assertEquals(listOf(group("dev")), state.moduleGroups[fallback])
    assertTrue(state.memberToModuleMap.isEmpty(), "no sdkModule means no member→module mapping")
  }

  @Test
  fun `empty workspace without sdk module name produces empty state`() {
    val state = PyInstallWorkspaceState.compute(
      members = emptyList(),
      groupsByMember = emptyMap(),
      moduleMap = emptyMap(),
      sdkModuleName = null,
      sdkModule = null,
      preselectModuleName = null,
    )

    assertTrue(state.availableModules.isEmpty())
    assertNull(state.selectedModule)
  }

  @Test
  fun `sdkModuleName triggers the synthetic-fallback path regardless of preselect`() {
    // moduleMap is empty AND sdkModuleName is non-null, so the function short-circuits to the
    // synthetic-member fallback before consulting preselectModuleName. Documents the precedence
    // explicitly so a future reader doesn't expect preselect to win in this branch.
    val main = member("main")
    val tests = member("tests")
    val state = PyInstallWorkspaceState.compute(
      members = listOf(main, tests),
      groupsByMember = emptyMap(),
      moduleMap = emptyMap(),
      sdkModuleName = "main",
      sdkModule = null,
      preselectModuleName = "tests",
    )

    val synthetic = state.availableModules.single()
    assertEquals("main", synthetic.name)
    assertEquals(synthetic, state.selectedModule)
  }

  @Test
  fun `unknown preselect name falls back to first member`() {
    val main = member("main")
    val tests = member("tests")
    val state = PyInstallWorkspaceState.compute(
      members = listOf(main, tests),
      groupsByMember = emptyMap(),
      moduleMap = emptyMap(),
      sdkModuleName = null,
      sdkModule = null,
      preselectModuleName = "missing",
    )

    assertEquals(main, state.selectedModule, "first member is the documented fallback")
  }

  private class FakeWorkspace(
    private val membersByQuery: Map<String, List<PyWorkspaceMember>> = emptyMap(),
    private val groupsByQuery: Map<String, Map<PyWorkspaceMember, List<PyDependencyGroup>>> = emptyMap(),
    private val moduleByMember: Map<PyWorkspaceMember, Module> = emptyMap(),
    var lastQueryName: String? = null,
  ) : PythonWorkspaceSupport {
    override suspend fun getWorkspaceMembers(projectName: ProjectName): List<PyWorkspaceMember> {
      lastQueryName = projectName.name
      return membersByQuery[projectName.name] ?: emptyList()
    }
    override suspend fun getDependencyGroups(projectName: ProjectName): Map<PyWorkspaceMember, List<PyDependencyGroup>> =
      groupsByQuery[projectName.name] ?: emptyMap()
    override suspend fun resolveModule(member: PyWorkspaceMember): Module? = moduleByMember[member]
  }

  @Test
  fun `loadWorkspaceState queries workspace under sdkModuleName when present`() = runTest {
    val main = member("main")
    val workspace = FakeWorkspace(membersByQuery = mapOf("main" to listOf(main)))

    val state = PyInstallWorkspaceState.load(
      workspace = workspace,
      projectFallbackName = "fallback-project",
      sdkModuleName = "main",
      sdkModule = null,
      preselectModuleName = null,
    )

    assertEquals("main", workspace.lastQueryName)
    assertEquals(listOf(main), state.availableModules)
    assertEquals(main, state.selectedModule)
  }

  @Test
  fun `loadWorkspaceState falls back to projectFallbackName when sdkModuleName is null`() = runTest {
    val workspace = FakeWorkspace()

    PyInstallWorkspaceState.load(
      workspace = workspace,
      projectFallbackName = "project-X",
      sdkModuleName = null,
      sdkModule = null,
      preselectModuleName = null,
    )

    assertEquals("project-X", workspace.lastQueryName)
  }

  @Test
  fun `loadWorkspaceState returns empty state when workspace support is null`() = runTest {
    val state = PyInstallWorkspaceState.load(
      workspace = null,
      projectFallbackName = "anything",
      sdkModuleName = null,
      sdkModule = null,
      preselectModuleName = null,
    )

    assertTrue(state.availableModules.isEmpty())
    assertNull(state.selectedModule)
    assertTrue(state.memberToModuleMap.isEmpty())
  }

  @Test
  fun `loadWorkspaceState carries dependency groups returned by workspace`() = runTest {
    val main = member("main")
    val groups = mapOf(main to listOf(group("dev")))
    val workspace = FakeWorkspace(
      membersByQuery = mapOf("main" to listOf(main)),
      groupsByQuery = mapOf("main" to groups),
    )

    val state = PyInstallWorkspaceState.load(
      workspace = workspace,
      projectFallbackName = "ignored",
      sdkModuleName = "main",
      sdkModule = null,
      preselectModuleName = null,
    )

    assertEquals(groups, state.moduleGroups)
  }

  @Test
  fun `groupsByMember and moduleMap pass through untouched`() {
    val main = member("main")
    val groups = mapOf(main to listOf(group("dev"), group("test")))
    val state = PyInstallWorkspaceState.compute(
      members = listOf(main),
      groupsByMember = groups,
      moduleMap = emptyMap(),
      sdkModuleName = null,
      sdkModule = null,
      preselectModuleName = null,
    )

    assertEquals(groups, state.moduleGroups)
    assertTrue(state.memberToModuleMap.isEmpty())
  }
}
