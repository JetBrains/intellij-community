// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.interpreters

import com.intellij.openapi.module.Module
import com.intellij.python.community.execService.*
import com.intellij.python.community.execService.impl.transformerToHandler
import com.intellij.python.community.execService.python.HelperName
import com.intellij.python.community.execService.python.advancedApi.executeHelperAdvanced
import com.intellij.python.community.execService.python.advancedApi.executePythonAdvanced
import com.intellij.python.community.interpreters.impl.InterpreterFields
import com.intellij.python.community.interpreters.impl.InterpreterServiceImpl
import com.intellij.python.community.services.shared.PythonWithLanguageLevel
import com.intellij.python.community.services.shared.PythonWithUi
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.Nls
import java.nio.file.Path


/**
 * Python interpreter can be either valid or invalid (broken at the moment it was loaded)
 */
sealed interface Interpreter : InterpreterFields

/**
 * Interpreter was usable at the moment it was loaded.
 * It has [ui] and [getReadableName] and can be used to execute code against [ExecService] (see extension functions)
 */
interface ValidInterpreter : PythonWithLanguageLevel, PythonWithUi, Interpreter

/**
 * At the moment of loading this interpreter was invalid due to [invalidMessage].
 */
interface InvalidInterpreter : Interpreter {
  val invalidMessage: @Nls String
}

/**
 * Obtain it by means of eponymous function.
 */
interface InterpreterService {
  /**
   * Which interpreters might be used for this directory?
   */
  suspend fun getInterpreters(projectDir: Path): List<Interpreter>

  /**
   * Is there an [Interpreter] associated with this [module]?
   */
  suspend fun getForModule(module: Module): Interpreter?
}

/***
 * ```kotlin
 * InterpreterService().getInterpreters(path)
 * ```
 */
fun InterpreterService(): InterpreterService = InterpreterServiceImpl


suspend fun InterpreterService.getValidInterpreters(projectDir: Path): List<ValidInterpreter> =
  getInterpreters(projectDir).mapNotNull {
    when (it) {
      is InvalidInterpreter -> null
      is ValidInterpreter -> it
    }
  }

/**
 * Execute [helper] on [python]. For remote eels, [helper] is copied (but only one file!).
 * Returns `stdout`
 */
suspend fun ExecService.executeHelper(
  python: ValidInterpreter,
  helper: HelperName,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> =
  executeHelperAdvanced(python.asExecutablePython, helper, args, options, procListener, ZeroCodeStdoutTransformer)

/**
 * Execute command on [python].
 * Returns `stdout`
 */
suspend fun ExecService.executeGetStdout(
  python: ValidInterpreter,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> =
  executePythonAdvanced(python.asExecutablePython, Args(*args.toTypedArray()), options, transformerToHandler(procListener, ZeroCodeStdoutTransformer))