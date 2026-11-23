// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.remoteSourcesLocalPath
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

/**
 * The interpreter of the main Python project, for a caller that cannot suspend and runs after startup, such as a
 * lesson or an action. `null` before the first snapshot, and when the project has no main Python project. A coroutine
 * calls [com.intellij.python.pyproject.model.evolution.findMainPythonInterpreter], which waits.
 */
@Internal
fun Project.mainPythonInterpreterIfReady(): PythonInterpreter? = findMainEvoPyProjectIfReady()?.interpreter

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


/**
 * Where a "remote_sources" folder for certain SDK is stored
 */
@get:Internal
internal val Sdk.remoteSourcesLocalPath: Path
  get() = remoteSourcesLocalPath(sdkAdditionalData as? PyTargetAwareAdditionalData
                                 ?: error("Only legacy and remote SDK and target-based SDKs are supported"))

