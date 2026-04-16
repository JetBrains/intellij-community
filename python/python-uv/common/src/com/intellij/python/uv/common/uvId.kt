// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.common

import com.intellij.python.common.tools.ToolId
import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import com.jetbrains.python.PyToolUIInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val UV_TOOL_ID: ToolId = ToolId("uv")
@ApiStatus.Internal
val UV_BASE_TOOL_ID: ToolId = ToolId("uvBase")

// TODO: Move this symbol to backend as soon as all usages are moved to backend
@ApiStatus.Internal
val UV_UI_INFO: PyToolUIInfo = PyToolUIInfo("uv", PythonUvCommonIcons.UV)
