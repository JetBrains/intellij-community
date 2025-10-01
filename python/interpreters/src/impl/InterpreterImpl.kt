// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.interpreters.impl

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.interpreters.Interpreter
import com.intellij.python.community.interpreters.InvalidInterpreter
import com.intellij.python.community.interpreters.ValidInterpreter
import com.intellij.python.community.services.shared.PythonWithName
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*

internal class ValidInterpreterImpl(
  override val languageLevel: LanguageLevel,
  override val asExecutablePython: ExecutablePython,
  private val mixin: SdkMixin,
  override val ui: PyToolUIInfo?,
) : ValidInterpreter, InterpreterFields by mixin {
  override fun toString(): String {
    return "ValidInterpreterImpl(languageLevel=$languageLevel, asExecutablePython=$asExecutablePython)"
  }
}


internal class InvalidInterpreterImpl(
  private val mixin: SdkMixin,
  override val invalidMessage: @Nls String,
) : InvalidInterpreter, InterpreterFields by mixin {
  override fun toString(): String {
    return "InvalidInterpreterImpl(invalidMessage='$invalidMessage')"
  }
}

@ApiStatus.NonExtendable
interface InterpreterFields : PythonWithName {
  /**
   * Currently, interpreters are based on SDK. But this is an implementation detail to be changed soon.
   * Do not use this field unless absolutely necessary.
   */
  val sdk: Sdk
  val id: UUID
}

internal class SdkMixin(override val sdk: Sdk, data: PythonSdkAdditionalData) : InterpreterFields {
  override val id: UUID = data.uuid

  override suspend fun getReadableName(): @Nls String = sdk.name
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Interpreter) return false

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}
