// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.getPythonInfo
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * A local Python that runs a helper. The helper does not need the interpreter of the project, for example pycodestyle or
 * the RST renderer. [languageLevel] is the language level of [binary], so the caller can select a helper that it runs.
 */
@ApiStatus.Internal
data class LocalHelperPython(val binary: PythonBinary, val languageLevel: LanguageLevel)

/**
 * The local Python for a helper, see [LocalHelperPython].
 *
 * It is the interpreter of the Python project of [module] when that is a local CPython. Else it is the interpreter of the
 * main Python project of [project], under the same condition. Else it is the best system Python of the local machine.
 * Without [project], only a system Python is used. The result is `null` when the machine has no Python. The function
 * reads the snapshot and does not wait for it.
 */
@ApiStatus.Internal
suspend fun findLocalHelperPython(project: Project?, module: Module?): LocalHelperPython? {
  val interpreters = listOfNotNull(module?.findEvoPyProjectIfReady()?.interpreter, project?.findMainEvoPyProjectIfReady()?.interpreter)
  for (interpreter in interpreters) {
    interpreter.asLocalHelperPython()?.let { return it }
  }
  val systemPython = SystemPythonService().findSystemPythons(localEel).firstOrNull() ?: return null
  return LocalHelperPython(systemPython.pythonBinary, systemPython.pythonInfo.languageLevel)
}

/** [findLocalHelperPython] for a caller that cannot suspend, such as Java code. */
@ApiStatus.Internal
@RequiresBackgroundThread
fun findLocalHelperPythonBlocking(project: Project?, module: Module?): LocalHelperPython? =
  runBlockingMaybeCancellable { findLocalHelperPython(project, module) }

/** This interpreter as a [LocalHelperPython], or `null` when it is not a local CPython or its version is not known. */
@ApiStatus.Internal
fun PythonInterpreter.asLocalHelperPython(): LocalHelperPython? = when (this) {
  is NativePythonInterpreter -> when {
    pythonBinary.getEelDescriptor() != LocalEelDescriptor -> null
    PythonSdkFlavor.getFlavor(pythonBinary.toString()) !is CPythonSdkFlavor -> null
    else -> getPythonInfo().successOrNull?.let { LocalHelperPython(pythonBinary, it.languageLevel) }
  }
  is TargetPythonInterpreter -> null
}
