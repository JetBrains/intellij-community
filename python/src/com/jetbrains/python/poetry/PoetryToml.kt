// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry

import com.intellij.openapi.module.Module
import com.intellij.util.FileName
import com.jetbrains.python.sdk.findAmongRoots
import java.nio.file.Path

val POETRY_TOML: FileName = FileName("poetry.toml")

internal suspend fun findPoetryToml(module: Module): Path? = module.findAmongRoots(POETRY_TOML)

