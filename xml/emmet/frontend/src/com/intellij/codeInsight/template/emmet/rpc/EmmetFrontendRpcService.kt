// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.rpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Scope container for RPC invocations
 */
@Service(PROJECT)
internal class EmmetFrontendRpcService(private val cs: CoroutineScope) {
  companion object {
    fun scope(project: Project): CoroutineScope = project.service<EmmetFrontendRpcService>().cs
  }
}