// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.declaration

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PoetryConst {
  const val TOOLS_POETRY_DEPENDENCIES: String = "tool.poetry.dependencies"
  const val TOOLS_POETRY_DEV_DEPENDENCIES: String = "tool.poetry.dependencies"
  const val DEPENDENCIES: String = "dependencies"
  const val PROJECT: String = "project"
}