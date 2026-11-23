// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyModuleInterpreterSetter")

package com.jetbrains.python.configuration

import com.intellij.openapi.module.Module
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.writeRequirementsPath
import com.jetbrains.python.PyBundle
import java.nio.file.Path
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.project.asPyProjectOrCandidate
import com.jetbrains.python.sdk.runWithSdkConfigurationLock
import org.jetbrains.annotations.ApiStatus

/**
 * [setPythonInterpreter] for a settings UI that applies on the EDT and cannot suspend, such as the Python facet of a Java
 * IDE. It runs under a modal progress and the configuration lock. A module that is not a Python project yet is a
 * candidate, see [asPyProjectOrCandidate], and the interpreter makes it one. The UI does not read the snapshot back, so
 * it does not wait for it.
 *
 * The modal progress cannot start in a write action, such as the apply of a facet editor in Project Structure. Such a
 * caller calls it later, when no modal dialog is open.
 */
@ApiStatus.Internal
@RequiresEdt
fun setPythonInterpreterBlocking(module: Module, interpreter: PythonInterpreter?) {
  runWithSdkConfigurationLock(module.project) {
    val result = module.asPyProjectOrCandidate()?.setPythonInterpreter(interpreter, waitForSnapshot = false)
    if (result is Result.Failure) ErrorSink().emit(result.error, module.project)
  }
}

/**
 * Stores [path] as the requirements file of [interpreter], see [writeRequirementsPath], for a settings UI that applies
 * on the EDT and cannot suspend. It runs under a modal progress. It writes only the interpreter facts and does not wait
 * for the project model.
 */
@ApiStatus.Internal
@RequiresEdt
fun writeRequirementsPathBlocking(interpreter: PythonInterpreter, path: Path?) {
  runWithModalProgressBlocking(interpreter.project, PyBundle.message("form.integrated.tools.package.requirements.file.saving")) {
    interpreter.writeRequirementsPath(path)
  }
}
