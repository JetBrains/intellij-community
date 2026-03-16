// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.common

import com.intellij.ide.util.PropertiesComponent
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.jetbrains.python.PyToolUIInfo
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemDependent

@ApiStatus.Internal
val POETRY_TOOL_ID: ToolId = ToolId("poetry")

@ApiStatus.Internal
val POETRY_UI_INFO: PyToolUIInfo = PyToolUIInfo("Poetry", PythonCommunityImplPoetryCommonIcons.Poetry)

/**
 * Tells if the SDK was added as poetry.
 * The user-set persisted a path to the poetry executable.
 */
var PropertiesComponent.poetryPath: @SystemDependent String?
  get() = getValue(POETRY_PATH_SETTING)
  set(value) {
    setValue(POETRY_PATH_SETTING, value)
  }
private const val POETRY_PATH_SETTING: String = "PyCharm.Poetry.Path"
