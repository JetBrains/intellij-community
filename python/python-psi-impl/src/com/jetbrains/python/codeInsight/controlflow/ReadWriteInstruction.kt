/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyAnyType.Companion.unknown
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.NonNls

class ReadWriteInstruction private constructor(
  builder: ControlFlowBuilder,
  element: PsiElement?,
  val name: String?,
  val access: ACCESS,
  getType: InstructionTypeCallback? = null,
) : InstructionImpl(builder, element) {
  enum class ACCESS(val isReadAccess: Boolean, val isWriteAccess: Boolean, val isAssertTypeAccess: Boolean, val isDeleteAccess: Boolean) {
    READ(true, false, false, false),
    WRITE(false, true, false, false),
    ASSERTTYPE(false, false, true, false),
    READWRITE(true, true, false, false),
    DELETE(false, false, false, true)
  }

  private val myGetType: InstructionTypeCallback = getType ?: instructionTypeCallback(element)

  fun getType(context: TypeEvalContext?, anchor: PsiElement?): Ref<PyType?>? {
    return myGetType.getType(context)
  }

  @NonNls
  override fun getElementPresentation(): @NonNls String {
    return access.toString() + " ACCESS: " + this.name
  }

  companion object {
    private fun instructionTypeCallback(element: PsiElement?): InstructionTypeCallback {
      return if (element is PyExpression)
        InstructionTypeCallback { Ref.create(it.getType(element)) }
      else
        InstructionTypeCallback { Ref.create(unknown) }
    }

    fun read(
      builder: ControlFlowBuilder,
      element: PyElement?,
      name: String?,
    ): ReadWriteInstruction {
      return ReadWriteInstruction(builder, element, name, ACCESS.READ)
    }

    fun write(
      builder: ControlFlowBuilder,
      element: PyElement?,
      name: String?,
    ): ReadWriteInstruction {
      return ReadWriteInstruction(builder, element, name, ACCESS.WRITE)
    }

    fun newInstruction(
      builder: ControlFlowBuilder,
      element: PsiElement?,
      name: String?,
      access: ACCESS,
    ): ReadWriteInstruction {
      return ReadWriteInstruction(builder, element, name, access)
    }

    fun assertType(
      builder: ControlFlowBuilder,
      element: PsiElement?,
      name: String?,
      getType: InstructionTypeCallback?,
    ): ReadWriteInstruction {
      return ReadWriteInstruction(builder, element, name, ACCESS.ASSERTTYPE, getType)
    }

    fun readWrite(
      builder: ControlFlowBuilder,
      element: PsiElement?,
      name: String?,
      getType: InstructionTypeCallback?,
    ): ReadWriteInstruction {
      return ReadWriteInstruction(builder, element, name, ACCESS.READWRITE, getType)
    }
  }
}
