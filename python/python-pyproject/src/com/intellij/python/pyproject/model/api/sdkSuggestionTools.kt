package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.suggestSdkImpl


sealed interface SuggestedSdk {
  /**
   * Part of workspace with [parentModule] as a root
   */
  data class SameAs(val parentModule: Module, val accordingTo: ToolId) : SuggestedSdk

  /**
   * Standalone module. When possible, use one of [preferTools] to configure it
   */
  data class PyProjectIndependent(val preferTools: Set<ToolId>) : SuggestedSdk
}


/**
 * Suggests how to configure SDK for a certain module.
 * `null` means this module is not `pyproject.toml` based
 */
suspend fun Module.suggestSdk(): SuggestedSdk? = suggestSdkImpl(this)