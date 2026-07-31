// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.module.Module
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonWorkspaceSupport

/**
 * Immutable snapshot of the install dialog's workspace-selector state.
 *
 * The constructor is private on purpose — callers are expected to obtain instances via
 * [Companion.load] (suspending, queries a [PythonWorkspaceSupport]) or [Companion.compute] (pure,
 * for tests and callers that already have the raw workspace inputs materialised). Nobody should
 * assemble a state by hand; every field has invariants that only the factories enforce.
 */
internal class PyInstallWorkspaceState private constructor(
  val availableModules: List<PyWorkspaceMember>,
  val moduleGroups: Map<PyWorkspaceMember, List<PyDependencyGroup>>,
  val memberToModuleMap: Map<PyWorkspaceMember, Module>,
  val displayModuleName: String?,
  val selectedModule: PyWorkspaceMember?,
) {

  companion object {
    /**
     * Pulls the current workspace layout for the install dialog: queries [workspace] for the
     * member list, dependency groups and module-to-member mapping using [sdkModuleName]
     * (falling back to [projectFallbackName] when the SDK is not bound to a module), then folds
     * the result into a [PyInstallWorkspaceState] via [compute] using [sdkModule] and
     * [preselectModuleName] to decide the initial selection.
     *
     * Extracted out of the dialog so the orchestration can be unit-tested against a fake
     * [PythonWorkspaceSupport] without spinning up a Swing tree.
     */
    suspend fun load(
      workspace: PythonWorkspaceSupport?,
      projectFallbackName: String,
      sdkModuleName: String?,
      sdkModule: Module?,
      preselectModuleName: String?,
    ): PyInstallWorkspaceState {
      val queryName = sdkModuleName ?: projectFallbackName
      val members = workspace?.getWorkspaceMembers(ProjectName(queryName)) ?: emptyList()
      val groupsByMember = workspace?.getDependencyGroups(ProjectName(queryName)) ?: emptyMap()
      val moduleMap = members.mapNotNull { member ->
        workspace?.resolveModule(member)?.let { module -> member to module }
      }.toMap()
      return compute(
        members = members,
        groupsByMember = groupsByMember,
        moduleMap = moduleMap,
        sdkModuleName = sdkModuleName,
        sdkModule = sdkModule,
        preselectModuleName = preselectModuleName,
      )
    }

    /**
     * Folds raw workspace inputs into a [PyInstallWorkspaceState].
     *
     * All three of [sdkModuleName], [sdkModule] and [preselectModuleName] may be `null` at the
     * same time — that's the "no interpreter or the interpreter is bound to nothing named"
     * case. In that path the state degrades to `availableModules=members, selectedModule=first`
     * (or empty when members is also empty). The synthetic-member fallback (first branch) only
     * triggers when [sdkModuleName] is non-null but no workspace member matches it.
     */
    fun compute(
      members: List<PyWorkspaceMember>,
      groupsByMember: Map<PyWorkspaceMember, List<PyDependencyGroup>>,
      moduleMap: Map<PyWorkspaceMember, Module>,
      sdkModuleName: String?,
      sdkModule: Module?,
      preselectModuleName: String?,
    ): PyInstallWorkspaceState {
      if (moduleMap.isEmpty() && sdkModuleName != null) {
        val fallback = PyWorkspaceMember(sdkModuleName)
        val groups = if (groupsByMember.isNotEmpty()) mapOf(fallback to groupsByMember.values.first()) else emptyMap()
        val memberMap = sdkModule?.let { mapOf(fallback to it) } ?: emptyMap()
        return PyInstallWorkspaceState(
          availableModules = listOf(fallback),
          moduleGroups = groups,
          memberToModuleMap = memberMap,
          displayModuleName = null,
          selectedModule = fallback,
        )
      }
      val selected = preselectModuleName?.let { name -> members.find { it.name == name } }
                     ?: moduleMap.entries.find { (_, m) -> m == sdkModule }?.key
                     ?: members.find { it.name == sdkModuleName }
                     ?: members.firstOrNull()
      return PyInstallWorkspaceState(
        availableModules = members,
        moduleGroups = groupsByMember,
        memberToModuleMap = moduleMap,
        displayModuleName = null,
        selectedModule = selected,
      )
    }
  }
}
