// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyModuleInterpreterSetter")

package com.jetbrains.python.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.sdk.asPyProjectAddingFacet
import com.jetbrains.python.sdk.runWithSdkConfigurationLock

/**
 * [setPythonInterpreter] for the Java Settings page, which applies on the EDT and cannot suspend. It runs under a modal
 * progress and the configuration lock. Settings does not read the snapshot back, so it does not wait for it.
 */
@RequiresEdt
internal fun setPythonInterpreterBlocking(module: Module, sdk: Sdk?) {
  runWithSdkConfigurationLock(module.project) {
    // A broken SDK names no interpreter, so the module gets none, as for no SDK.
    val result = module.asPyProjectAddingFacet()?.setPythonInterpreter(sdk?.pythonInterpreterResultAsync()?.successOrNull, waitForSnapshot = false)
    if (result is Result.Failure) ErrorSink().emit(result.error, module.project)
  }
}

/**
 * The module whose interpreter the Settings page shows and sets: [module], or the module of the main Python project
 * when the page has none. `null` when the project has no Python project, so it has no interpreter. The page opens
 * after startup, when the project structure has loaded.
 */
internal fun findInterpreterModule(project: Project, module: Module?): Module? =
  module ?: project.findMainEvoPyProjectIfReady()?.pyProject?.residesOnModule

