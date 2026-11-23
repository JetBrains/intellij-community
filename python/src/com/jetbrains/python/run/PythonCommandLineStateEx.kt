// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.run.features.resolveRunToolParameters
import org.jetbrains.annotations.ApiStatus

/**
 * To be used by [PythonCommandLineState] only
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun AbstractPythonRunConfiguration<*>.resolveRunToolParametersForJvm(interpreter: PythonInterpreter): PyRunToolParameters? =
  runBlockingMaybeCancellable { resolveRunToolParameters(interpreter) }