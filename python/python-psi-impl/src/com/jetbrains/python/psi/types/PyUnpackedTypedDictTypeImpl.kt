package com.jetbrains.python.psi.types

import com.intellij.openapi.util.NlsSafe
import java.util.Objects

internal class PyUnpackedTypedDictTypeImpl(
  private val typedDictType: PyTypedDictType,
) : PyUnpackedTypedDictType {

  override fun getTypedDictType(): PyTypedDictType = typedDictType

  override fun getUnpackedParameters(context: TypeEvalContext): List<PyCallableParameter> {
    return typedDictType.toClass().getParameters(context) ?: emptyList()
  }

  override val name: @NlsSafe String = "Unpack[" + typedDictType.name + "]"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PyUnpackedTypedDictTypeImpl) return false
    return typedDictType == other.typedDictType
  }

  override fun hashCode(): Int = Objects.hash(typedDictType)

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyUnpackedTypedDictType(this)
    }
    return visitor.visitPyType(this)
  }
}
