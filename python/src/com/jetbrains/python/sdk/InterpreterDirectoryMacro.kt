// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.ide.macro.Macro
import com.intellij.ide.macro.PathMacro
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.util.io.FileUtil
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.findModuleInterpreterIfReady

internal class InterpreterDirectoryMacro : Macro(), PathMacro {
  override fun getName(): String = "PyInterpreterDirectory"

  override fun getDescription(): String = PyBundle.message("python.sdk.directory.macro.description")

  override fun expand(dataContext: DataContext): String? {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null
    val module = PlatformCoreDataKeys.MODULE.getData(dataContext)
    // Without a module, the interpreter of the main Python project.
    val interpreter = (if (module != null) findModuleInterpreterIfReady(module) else project.mainPythonInterpreterIfReady())
                      ?: return null
    return when (interpreter) {
      is NativePythonInterpreter -> interpreter.pythonBinary.parent?.let { FileUtil.toSystemDependentName(it.toString()) }
      // The directory of an interpreter on a target is no local path.
      is TargetPythonInterpreter -> null
    }
  }
}
