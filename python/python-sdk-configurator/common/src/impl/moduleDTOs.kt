package com.intellij.python.sdkConfigurator.common.impl

import com.intellij.openapi.util.NlsSafe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.Serializable

typealias ModuleName = @NlsSafe String
typealias ToolIdDTO = @NlsSafe String // value classes aren't serializable by default
typealias ModulePath = @NlsSafe String? // Path can't be sent to the other OS

/**
 * Module and how do we create it.
 * [ModulePath] is a string to be shown to user (might be null if module is not pyproject.toml based)
 */
@Serializable
data class ModuleDTO(
  val name: ModuleName,
  val path: ModulePath,
  val createdByTool: ToolIdDTO,
  val existingPyVersion: @NlsSafe String?,
  val childModules: ImmutableList<ModuleName>,
)

@Serializable
@JvmInline
value class ModulesDTO(val modules: List<ModuleDTO>)