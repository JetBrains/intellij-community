package com.intellij.python.sdkConfigurator.common.impl

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable

typealias ModuleName = @NlsSafe String
typealias ToolIdDTO = @NlsSafe String // value classes aren't serializable by default

// Serializable DTO that reflects regular object is, unfortunately, recommended approach

@Serializable
sealed interface CreateSdkDTO {
  /**
   * This module is part of workspace and parent is [parentModuleName]
   */
  @Serializable
  data class SameAs(val parentModuleName: ModuleName) : CreateSdkDTO

  /**
   * [createdByTool] can create an SDK for this module (if [existingVersion] is not null, venv is already exists on disk)
   */
  @Serializable
  data class ConfigurableModule(val existingVersion: @NlsSafe String?, val createdByTool: ToolIdDTO) : CreateSdkDTO
}

/**
 * Module and how do we create it
 */
@Serializable
data class ModulesDTO(val modules: Map<ModuleName, CreateSdkDTO>)