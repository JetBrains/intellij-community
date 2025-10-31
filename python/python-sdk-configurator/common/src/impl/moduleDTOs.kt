package com.intellij.python.sdkConfigurator.common.impl

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable

typealias ModuleName = @NlsSafe String
typealias ToolIdDTO = @NlsSafe String // value classes aren't serializable by default


/**
 * Module and how do we create it
 */
@Serializable
data class ModuleDTO(
  val name: ModuleName,
  val createdByTool: ToolIdDTO,
  val existingPyVersion: @NlsSafe String?,
  val childModules: List<ModuleName>,
)

@Serializable
@JvmInline
value class ModulesDTO(val modules: List<ModuleDTO>)