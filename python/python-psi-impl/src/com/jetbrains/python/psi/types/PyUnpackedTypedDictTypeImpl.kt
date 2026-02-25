package com.jetbrains.python.psi.types

import com.intellij.openapi.util.NlsSafe
import java.util.Objects

internal class PyUnpackedTypedDictTypeImpl(
  private val originalParameters: List<PyCallableParameter>,
  private val typedDictType: PyTypedDictType,
) : PyUnpackedTypedDictType {

  override fun getUnpackedParameters(): List<PyCallableParameter> = originalParameters
  override fun getTypedDictType(): PyTypedDictType = typedDictType

  override val name: @NlsSafe String = "Unpack[" + typedDictType.name + "]"

  companion object {
    @JvmStatic
    fun fromTypedDict(typedDictType: PyTypedDictType, context: TypeEvalContext): PyUnpackedTypedDictTypeImpl {
      return PyUnpackedTypedDictTypeImpl(typedDictType.toClass().getParameters(context) ?: emptyList(), typedDictType)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PyUnpackedTypedDictTypeImpl) return false
    return originalParameters == other.originalParameters && typedDictType == other.typedDictType
  }

  override fun hashCode(): Int = Objects.hash(originalParameters, typedDictType)

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyUnpackedTypedDictType(this)
    }
    return visitor.visitPyType(this)
  }
}
