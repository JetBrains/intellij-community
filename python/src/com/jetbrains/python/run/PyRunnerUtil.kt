// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyRunnerUtil")

package com.jetbrains.python.run

import com.intellij.execution.configurations.RunProfileState
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

/**
 * Checks if [this] state (typically created using a run configuration) is [PythonCommandLineState] and the Python interpreter assigned to
 * it is based on a target.
 *
 * Handles "Project Default" Python interpreter resolving it to the interpreter assigned to the corresponding module of the run
 * configuration.
 */
@ApiStatus.Internal
internal fun RunProfileState.isTargetBasedSdkAssigned(): Boolean =
  (this as? PythonCommandLineState)?.sdk?.sdkAdditionalData is PyTargetAwareAdditionalData

internal val Sdk.isTargetBased: Boolean
  @ApiStatus.Internal
  get() = sdkAdditionalData is PyTargetAwareAdditionalData

@ApiStatus.Internal
internal fun RunProfileState.getModule(): Module? = (this as? PythonCommandLineState)?.config?.module