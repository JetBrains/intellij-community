package com.jetbrains.python.psi.types

/**
 * Type of typing.Concatenate to store corresponding first type and parameter specification
 */
class PyConcatenateType(val firstTypes: List<PyType?>, val paramSpec: PyParamSpecType?) : PyCallableParameterVariadicType {
  override fun getName(): String = "Concatenate(${firstTypes.joinToString { it?.name ?: "Any" }}, ${paramSpec?.name ?: "..."})"

  override fun <T : Any?> acceptTypeVisitor(visitor: PyTypeVisitor<T?>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyConcatenateType(this)
    }
    return visitor.visitPyType(this)
  }

}