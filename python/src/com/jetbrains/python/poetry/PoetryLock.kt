// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry

import com.intellij.openapi.module.Module
import com.intellij.util.FileName
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.resolveFile
import java.nio.file.Path

val POETRY_LOCK: FileName = FileName("poetry.lock")

suspend fun findPoetryLock(module: Module): Path? = module.asPyProject()?.resolveFile(POETRY_LOCK)
