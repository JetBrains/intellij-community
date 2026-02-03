// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetedCommandLine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TargetProcessRunResult(val process: Process, val targetCommandLine: TargetedCommandLine, val targetEnvironment: TargetEnvironment) {
  val commandPresentation: String = targetCommandLine.getCommandPresentation(targetEnvironment)
}