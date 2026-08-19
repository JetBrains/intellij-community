// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl

import com.intellij.openapi.module.Module
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.SuggestedSdk
import com.intellij.python.pyproject.model.internal.suggestSdk


internal suspend fun Module.getSdkAssociatedModule(toolId: ToolId): Module = getRootModuleOrNull(toolId) ?: this

internal suspend fun Module.getRootModuleOrNull(toolId: ToolId): Module? =
  when (val r = suggestSdk()) {
    // Workspace suggested by uv
    is SuggestedSdk.SameAs -> if (r.accordingTo == toolId) r.parentModule else null
    null, is SuggestedSdk.PyProjectIndependent -> null
  }
