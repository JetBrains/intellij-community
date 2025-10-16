// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.uv.common

import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.uv.common.icons.PythonCommunityImplUVCommonIcons
import com.jetbrains.python.PyToolUIInfo

val UV_TOOL_ID: ToolId = ToolId("uv")

// TODO: Move this symbol to backend as soon as all usages are moved to backend
val UV_UI_INFO: PyToolUIInfo = PyToolUIInfo("uv", PythonCommunityImplUVCommonIcons.UV)
