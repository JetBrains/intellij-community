// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl

import com.intellij.openapi.module.Module
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.SuggestedSdk
import com.intellij.python.pyproject.model.internal.suggestSdk
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject


/**
 * The project whose directory holds the environment of [toolId] for this one: the root of the workspace it takes part
 * in, or this project itself when it takes part in none.
 *
 * `null` when the workspace root is no Python project. Its directory is then unknown, and this project's own
 * directory is the wrong one: a member holds neither the environment nor the lock file of its workspace (PY-92193).
 */
internal suspend fun PyProject.getSdkAssociatedPyProject(toolId: ToolId): PyProject? {
  val rootModule = residesOnModule.getRootModuleOrNull(toolId) ?: return this
  return rootModule.asPyProject()
}

internal suspend fun Module.getRootModuleOrNull(toolId: ToolId): Module? =
  when (val r = suggestSdk()) {
    // Workspace suggested by uv
    is SuggestedSdk.SameAs -> if (r.accordingTo == toolId) r.parentModule else null
    null, is SuggestedSdk.PyProjectIndependent -> null
  }
