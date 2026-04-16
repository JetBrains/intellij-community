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

class PyCallableParameterImpl @JvmOverloads internal constructor(
  @field:NlsSafe private val myName: @NlsSafe String? = null,
  private val myType: Ref<PyType?>? = null,
  private val myDefaultValue: PyExpression? = null,
  private val myDefaultValueText: String? = null,
  override val parameter: PyParameter? = null,
  private val myIsPositionalContainer: Boolean = false,
  private val myIsKeywordContainer: Boolean = false,
  private val myIsSelf: Boolean = false,
  private val myIsKeywordOnlySeparator: Boolean = false,
  private val myIsPositionalOnlySeparator: Boolean = false,
  private val myDeclarationElement: PsiElement? = null,
) : PyCallableParameter {
  @get:Nls
  override val name: @Nls String?
    get() = myName ?: parameter?.name

  override fun getType(context: TypeEvalContext): PyType? = when {
    myType != null -> myType.get()
    parameter is PyNamedParameter -> context.getType(parameter)
    else -> null
  }

  override val declarationElement: PsiElement?
    get() = myDeclarationElement ?: parameter

  override val defaultValue: PyExpression?
    get() = if (parameter == null) myDefaultValue else parameter.defaultValue

  override fun hasDefaultValue(): Boolean {
    return parameter?.hasDefaultValue() ?: (myDefaultValue != null || myDefaultValueText != null)
  }

  override val defaultValueText: String?
    get() = when {
      parameter != null -> parameter.defaultValueText
      myDefaultValue != null -> myDefaultValue.text
      myDefaultValueText != null -> myDefaultValueText
      else -> null
    }

  override val isPositionalContainer: Boolean
    get() = myIsPositionalContainer || (parameter as? PyNamedParameter)?.isPositionalContainer == true

  override val isKeywordContainer: Boolean
    get() = myIsKeywordContainer || (parameter as? PyNamedParameter)?.isKeywordContainer == true

  override val isSelf: Boolean
    get() = myIsSelf || parameter?.isSelf == true

  override val isPositionOnlySeparator: Boolean
    get() = parameter is PySlashParameter || myIsPositionalOnlySeparator

  override val isKeywordOnlySeparator: Boolean
    get() = parameter is PySingleStarParameter || myIsKeywordOnlySeparator

  override fun getPresentableText(includeDefaultValue: Boolean, context: TypeEvalContext?): String {
    return getPresentableText(includeDefaultValue, context, { it.isUnknown })
  }

  override fun getPresentableText(
    includeDefaultValue: Boolean,
    context: TypeEvalContext?,
    typeFilter: (PyType?) -> Boolean,
  ): String {
    if (parameter !is PyNamedParameter && parameter != null) {
      return PyUtil.getReadableRepr(parameter, false)
    }
    return buildString {
      append(ParamHelper.getNameInSignature(this@PyCallableParameterImpl))

      var renderedAsTyped = false
      if (context != null) {
        val argumentType = getArgumentType(context)
        if (!typeFilter(argumentType)) {
          append(": ")
          append(PythonDocumentationProvider.getTypeName(argumentType, context))
          renderedAsTyped = true
        }
      }

      if (includeDefaultValue) {
        append(ParamHelper.getDefaultValuePartInSignature(defaultValueText, renderedAsTyped) ?: "")
      }
    }
  }

  /**
   * Returns the expected type for an argument passed to this parameter.
   *
   * For most parameters, this is the same as [getType].
   * However, for variadic parameters, this method applies special handling:
   * - For positional containers (`*args`):
   *   - If the type is `tuple[T, ...]`, returns `T` (the element type)
   *   - If the type is `*tuple[T1, T2, ...]`, returns the unpacked tuple type itself
   * - For keyword containers (`**kwargs`):
   *   - If the type is a collection (e.g., `dict[str, V]`), returns `V` (the value type)
   *   - If the type is `Unpack[TypedDict]`, returns the unpacked TypedDict type
   *
   * @param context the type evaluation context
   * @return the expected type of the argument, or `null` if the type cannot be determined
   */
  override fun getArgumentType(context: TypeEvalContext): PyType? {
    val parameterType = getType(context)

    return if (isPositionalContainer && parameterType is PyTupleType) {
      // *args: str is equivalent to *args: *tuple[str, ...]
      // *args: *Ts is equivalent to *args: *tuple[*Ts]
      // Convert its type to a more general form of an unpacked tuple
      val unpackedTupleType = parameterType.asUnpackedTupleType()
      if (unpackedTupleType.isUnbound)
        unpackedTupleType.elementTypes.first()
      else
        unpackedTupleType
    }
    else if (isKeywordContainer) {
      if (parameterType is PyCollectionType) {
        parameterType.elementTypes.getOrNull(1)
      }
      else parameterType as? PyUnpackedTypedDictType ?: parameterType
    }
    else
      parameterType
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other == null || javaClass != other.javaClass) return false

    other as PyCallableParameterImpl
    return parameter == other.parameter &&
           myName == other.myName &&
           myType?.get() == other.myType?.get() &&
           myIsSelf == other.myIsSelf &&
           myIsPositionalContainer == other.myIsPositionalContainer &&
           myIsKeywordContainer == other.myIsKeywordContainer &&
           myIsKeywordOnlySeparator == other.myIsKeywordOnlySeparator &&
           myIsPositionalOnlySeparator == other.myIsPositionalOnlySeparator &&
           myDefaultValue == other.myDefaultValue &&
           myDefaultValueText == other.myDefaultValueText
  }

  override fun hashCode(): Int {
    return Objects.hash(
      parameter,
      myName,
      myType?.get(),
      myIsSelf,
      myIsPositionalContainer,
      myIsKeywordContainer,
      myIsKeywordOnlySeparator,
      myIsPositionalOnlySeparator,
      myDefaultValue,
      myDefaultValueText,
    )
  }

  companion object {
    @JvmStatic
    fun nonPsi(type: PyType?): PyCallableParameter = nonPsi(null, type, defaultValue = null)

    @JvmStatic
    @JvmOverloads
    fun nonPsi(name: String?, type: PyType?, defaultValue: PyExpression? = null): PyCallableParameter =
      PyCallableParameterImpl(name, Ref(type), defaultValue)

    @JvmStatic
    fun nonPsi(name: String?, type: PyType?, defaultValueText: String?): PyCallableParameter =
      PyCallableParameterImpl(name, Ref(type), myDefaultValueText = defaultValueText)

    fun nonPsi(name: String?, type: PyType?, defaultValue: PyExpression?, declarationElement: PsiElement): PyCallableParameter =
      PyCallableParameterImpl(name, Ref(type), defaultValue, myDeclarationElement = declarationElement)

    @JvmStatic
    fun positionalContainerNonPsi(name: String?, type: PyType?): PyCallableParameter =
      PyCallableParameterImpl(name, Ref(type), myIsPositionalContainer = true)

    @JvmStatic
    fun keywordContainerNonPsi(name: String?, type: PyType?): PyCallableParameter =
      PyCallableParameterImpl(name, Ref(type), myIsKeywordContainer = true)

    @JvmStatic
    fun keywordOnlySeparatorNonPsi(): PyCallableParameter =
      PyCallableParameterImpl(myIsKeywordOnlySeparator = true)

    @JvmStatic
    fun positionalOnlySeparatorNonPsi(): PyCallableParameter =
      PyCallableParameterImpl(myIsPositionalOnlySeparator = true)

    @JvmStatic
    fun psi(parameter: PyParameter): PyCallableParameter =
      PyCallableParameterImpl(parameter = parameter)

    @JvmStatic
    fun psi(parameter: PyParameter, type: PyType?): PyCallableParameter =
      PyCallableParameterImpl(myType = Ref(type), parameter =  parameter)
  }
}
