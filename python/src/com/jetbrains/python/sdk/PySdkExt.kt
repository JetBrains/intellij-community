// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.getSdkAPI
import org.jetbrains.annotations.ApiStatus

/**
 * The SDK of the interpreter of the main Python project, for external plugins. There is no project interpreter: an
 * interpreter belongs to a Python project. `null` before the first snapshot, and when the project has no main Python
 * project.
 */
@Deprecated(
  "An interpreter belongs to a Python project. Use the interpreter of the PyProject, or findMainPythonInterpreter for the main one.",
  ReplaceWith("mainPythonInterpreterIfReady()?.getSdkAPI()", "com.intellij.python.sdk.backend.getSdkAPI"),
  DeprecationLevel.ERROR
)
val Project.pythonSdk: Sdk?
  @ApiStatus.ScheduledForRemoval
  @Deprecated("An interpreter belongs to a Python project. Use the interpreter of the PyProject.")
  @Suppress("DEPRECATION") // It still answers an SDK.
  get() = mainPythonInterpreterIfReady()?.getSdkAPI()
