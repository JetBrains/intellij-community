// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyModuleInterpreterSetter")

package com.jetbrains.python.configuration

import com.jetbrains.python.sdk.asPyProjectAddingFacet
import com.intellij.openapi.module.Module
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.sdk.runWithSdkConfigurationLock

/**
 * [setPythonInterpreter] for the Java Settings page, which applies on the EDT and cannot suspend. It runs under a modal
 * progress and the configuration lock. Settings does not read the snapshot back, so it does not wait for it.
 */
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun setPythonInterpreterBlocking(module: Module, sdk: Sdk?) {
  runWithSdkConfigurationLock(module.project) {
    // A broken SDK names no interpreter, so the module gets none, as for no SDK.
    val result = module.asPyProjectAddingFacet()?.setPythonInterpreter(sdk?.pythonInterpreterResultAsync()?.successOrNull, waitForSnapshot = false)
    if (result is Result.Failure) ErrorSink().emit(result.error, module.project)
  }
}
