package com.jetbrains.python.psi.types

import com.intellij.openapi.util.NlsSafe
import java.util.*

internal class PyUnpackedTypedDictTypeImpl(
  private val originalParameters: List<PyCallableParameter>,
  private val wrapperType: PyType,
) : PyUnpackedTypedDictType {

  override fun getUnpackedParameters(): List<PyCallableParameter> = originalParameters
  override fun getWrapperType(): PyType = wrapperType
  override fun getName(): @NlsSafe String = "Unpack[" + wrapperType.name + "]"

  companion object {
    @JvmStatic
    fun fromTypedDict(typedDictType: PyTypedDictType, context: TypeEvalContext): PyUnpackedTypedDictTypeImpl {
      return PyUnpackedTypedDictTypeImpl(typedDictType.toClass().getParameters(context) ?: emptyList(), typedDictType)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PyUnpackedTypedDictTypeImpl) return false
    return originalParameters == other.originalParameters && wrapperType == other.wrapperType
  }

  override fun hashCode(): Int = Objects.hash(originalParameters, wrapperType)

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyUnpackedTypedDictType(this)
    }
    return visitor.visitPyType(this)
  }
}
