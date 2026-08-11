// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.impl

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.jetbrains.python.PyToolUIInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val HATCH_TOOL_ID: ToolId = ToolId("hatch")
val HATCH_UI_INFO: PyToolUIInfo = PyToolUIInfo("Hatch", PythonHatchIcons.Logo)

