// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.common

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.jetbrains.python.PyToolUIInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val POETRY_TOOL_ID: ToolId = ToolId("poetry")

@ApiStatus.Internal
val POETRY_UI_INFO: PyToolUIInfo = PyToolUIInfo("Poetry", PythonCommunityImplPoetryCommonIcons.Poetry)
