/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PySingleStarParameter
import com.jetbrains.python.psi.PySlashParameter
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.ParamHelper
import org.jetbrains.annotations.Nls
import java.util.Objects
import java.util.function.Predicate

class PyCallableParameterImpl internal constructor(
  @field:NlsSafe private val myName: @NlsSafe String?,
  private val myType: Ref<PyType?>?,
  private val myDefaultValue: PyExpression?,
  override val parameter: PyParameter?,
  private val myIsPositional: Boolean,
  private val myIsKeyword: Boolean,
  private val myDeclarationElement: PsiElement?,
) : PyCallableParameter {
  @get:Nls
  override val name: @Nls String?
    get() {
      if (myName != null) {
        return myName
      }
      else if (this.parameter != null) {
        return parameter.getName()
      }
      return null
    }

  override fun getType(context: TypeEvalContext): PyType? {
    if (myType != null) {
      return myType.get()
    }
    else if (this.parameter is PyNamedParameter) {
      return context.getType(this.parameter)
    }
    return null
  }

  override val declarationElement: PsiElement?
    get() {
      if (myDeclarationElement != null) return myDeclarationElement
      return parameter
    }

  override val defaultValue: PyExpression?
    get() = if (this.parameter == null) myDefaultValue else parameter.getDefaultValue()

  override fun hasDefaultValue(): Boolean {
    return if (this.parameter == null) myDefaultValue != null else parameter.hasDefaultValue()
  }

  override val defaultValueText: String?
    get() {
      if (this.parameter != null) return parameter.getDefaultValueText()
      return if (myDefaultValue == null) null else myDefaultValue.getText()
    }

  override val isPositionalContainer: Boolean
    get() {
      if (myIsPositional) return true

      val namedParameter = PyUtil.`as`<PyNamedParameter?>(this.parameter, PyNamedParameter::class.java)
      return namedParameter != null && namedParameter.isPositionalContainer()
    }

  override val isKeywordContainer: Boolean
    get() {
      if (myIsKeyword) return true

      val namedParameter = PyUtil.`as`<PyNamedParameter?>(this.parameter, PyNamedParameter::class.java)
      return namedParameter != null && namedParameter.isKeywordContainer()
    }

  override val isSelf: Boolean
    get() = this.parameter != null && parameter.isSelf()

  override val isPositionOnlySeparator: Boolean
    get() = this.parameter is PySlashParameter

  override val isKeywordOnlySeparator: Boolean
    get() = this.parameter is PySingleStarParameter

  override fun getPresentableText(includeDefaultValue: Boolean, context: TypeEvalContext?): String {
    return getPresentableText(includeDefaultValue, context, Predicate { obj: PyType? -> Objects.isNull(obj) })
  }

  override fun getPresentableText(
    includeDefaultValue: Boolean,
    context: TypeEvalContext?,
    typeFilter: Predicate<PyType?>,
  ): String {
    if (this.parameter is PyNamedParameter || this.parameter == null) {
      val sb = StringBuilder()

      sb.append(ParamHelper.getNameInSignature(this))

      var renderedAsTyped = false
      if (context != null) {
        val argumentType = getArgumentType(context)
        if (!typeFilter.test(argumentType)) {
          sb.append(": ")
          sb.append(PythonDocumentationProvider.getTypeName(argumentType, context))
          renderedAsTyped = true
        }
      }

      if (includeDefaultValue) {
        sb.append(ParamHelper.getDefaultValuePartInSignature(defaultValueText, renderedAsTyped) ?: "")
      }

      return sb.toString()
    }

    return PyUtil.getReadableRepr(this.parameter, false)
  }

  override fun getArgumentType(context: TypeEvalContext): PyType? {
    val parameterType = getType(context)

    if (isPositionalContainer && parameterType is PyTupleType) {
      // *args: str is equivalent to *args: *tuple[str, ...]
      // *args: *Ts is equivalent to *args: *tuple[*Ts]
      // Convert its type to a more general form of an unpacked tuple
      val unpackedTupleType = parameterType.asUnpackedTupleType()
      if (unpackedTupleType.isUnbound()) {
        return unpackedTupleType.getElementTypes().get(0)
      }
      return unpackedTupleType
    }
    else if (isKeywordContainer && parameterType is PyCollectionType) {
      return ContainerUtil.getOrElse<PyType?>(parameterType.getElementTypes(), 1, null)
    }

    return parameterType
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other == null || javaClass != other.javaClass) return false

    val parameter = other as PyCallableParameterImpl
    return myIsPositional == parameter.myIsPositional && myIsKeyword == parameter.myIsKeyword &&
           myName == parameter.myName &&
           Ref.deref<PyType?>(myType) == Ref.deref<PyType?>(parameter.myType) &&
           myDefaultValue == parameter.myDefaultValue &&
           this.parameter == parameter.parameter
  }

  override fun hashCode(): Int {
    return Objects.hash(
      myName, Ref.deref<PyType?>(myType), myDefaultValue,
      this.parameter, myIsPositional, myIsKeyword
    )
  }

  companion object {
    @JvmStatic
    fun nonPsi(type: PyType?): PyCallableParameter {
      return nonPsi(null, type)
    }

    @JvmStatic
    @JvmOverloads
    fun nonPsi(name: String?, type: PyType?, defaultValue: PyExpression? = null): PyCallableParameter {
      return PyCallableParameterImpl(name, Ref.create<PyType?>(type), defaultValue, null, false, false, null)
    }

    fun nonPsi(
      name: String?, type: PyType?, defaultValue: PyExpression?,
      declarationElement: PsiElement,
    ): PyCallableParameter {
      return PyCallableParameterImpl(name, Ref.create<PyType?>(type), defaultValue, null, false, false, declarationElement)
    }

    fun positionalNonPsi(name: String?, type: PyType?): PyCallableParameter {
      return PyCallableParameterImpl(name, Ref.create<PyType?>(type), null, null, true, false, null)
    }

    fun keywordNonPsi(name: String?, type: PyType?): PyCallableParameter {
      return PyCallableParameterImpl(name, Ref.create<PyType?>(type), null, null, false, true, null)
    }

    @JvmStatic
    fun psi(parameter: PyParameter): PyCallableParameter {
      return PyCallableParameterImpl(null, null, null, parameter, false, false, null)
    }

    @JvmStatic
    fun psi(parameter: PyParameter, type: PyType?): PyCallableParameter {
      return PyCallableParameterImpl(null, Ref.create<PyType?>(type), null, parameter, false, false, null)
    }
  }
}
