// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.python.hatch.HatchConfiguration
import com.jetbrains.python.sdk.ToolCommandSpec
import com.jetbrains.python.sdk.conda.CONDA_TOOL
import com.jetbrains.python.sdk.pipenv.PIPENV_TOOL
import com.jetbrains.python.sdk.poetry.POETRY_TOOL
import com.jetbrains.python.sdk.uv.impl.UV_TOOL_COMMAND_SPEC

internal val ADD_INTERPRETER_TOOL_COMMAND_SPECS: List<ToolCommandSpec> = listOf(
  CONDA_TOOL.toCommandSpec(),
  UV_TOOL_COMMAND_SPEC,
  PIPENV_TOOL.toCommandSpec(),
  POETRY_TOOL.toCommandSpec(),
  HatchConfiguration.toolCommandSpec,
)
