// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Whether the user can install a package into this interpreter. A native interpreter can. A target interpreter can
 * when the factory of its target says so. A target that no factory knows cannot.
 */
@ApiStatus.Internal
fun PythonInterpreter.isPackageManagementEnabled(): Boolean = when (this) {
  is NativePythonInterpreter -> true
  is TargetPythonInterpreter -> PythonInterpreterTargetEnvironmentFactory.isPackageManagementSupported(targetConfiguration) ?: false
}
