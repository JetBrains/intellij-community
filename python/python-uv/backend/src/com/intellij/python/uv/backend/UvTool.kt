// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend

import com.jetbrains.python.sdk.ToolCommandExecutor
import org.jetbrains.annotations.ApiStatus

/**
 * Locates the `uv` executable for [UvToolManagerProvider].
 *
 * 262 backport note: on master this lives alongside `setUvExecutableLocal` in this package; on 262 the
 * writer `setUvExecutableLocal` stays in `com.jetbrains.python.sdk.uv.impl`. Both share the
 * "PyCharm.Uv.Path" setting, so detection here stays consistent with the SDK setup flow.
 */
@ApiStatus.Internal
val UV_TOOL: ToolCommandExecutor = ToolCommandExecutor(
  "uv",
  getToolPathFromSettings = { getValue(UV_PATH_SETTING) },
)

private const val UV_PATH_SETTING: String = "PyCharm.Uv.Path"
