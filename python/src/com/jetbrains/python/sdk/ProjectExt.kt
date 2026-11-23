// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.jetbrains.python.sdk

import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import org.jetbrains.annotations.ApiStatus

/**
 * The interpreter of the main Python project, for a caller that cannot suspend and runs after startup, such as a
 * lesson or an action. `null` before the first snapshot, and when the project has no main Python project. A coroutine
 * calls [com.intellij.python.pyproject.model.evolution.findMainPythonInterpreter], which waits.
 */
fun Project.mainPythonInterpreterIfReady(): PythonInterpreter? = findMainEvoPyProjectIfReady()?.interpreter
