// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.interpreters.spi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.interpreters.Interpreter
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.sdk.flavors.PyFlavorData
import java.nio.file.Path

/**
 * Bridge between [Sdk] and [Interpreter].
 * Each [Sdk] has [PyFlavorData].
 *
 * [InterpreterProvider] must be found by [PyFlavorData] to convert [Interpreter]
 */
interface InterpreterProvider<T : PyFlavorData> {
  companion object {
    private val EP: ExtensionPointName<InterpreterProvider<*>> = ExtensionPointName<InterpreterProvider<*>>("Pythonid.interpreterProvider")

    @Suppress("UNCHECKED_CAST")
    fun <T : PyFlavorData> providerForData(data: T): InterpreterProvider<T>? =
      EP.extensionList.firstOrNull { it.flavorDataClass.isInstance(data) } as InterpreterProvider<T>?
  }

  val ui: PyToolUIInfo?
  val flavorDataClass: Class<T>
  suspend fun createExecutablePython(sdkHomePath: Path, flavorData: T): Result<ExecutablePython, MessageError>
}