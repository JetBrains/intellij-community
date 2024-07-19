package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import org.jetbrains.annotations.ApiStatus

/**
 * Class is used for representing TypeGuard and TypeIs behavior
 */
@ApiStatus.Internal
class PyNarrowedType private constructor(
  pyClass: PyClass,
  val qname: String,
  val narrowedType: PyType,
  val original: PyCallExpression,
  val negated: Boolean,
  val typeIs: Boolean)
  : PyClassTypeImpl(pyClass, false) {

    fun negate(): PyNarrowedType {
      return PyNarrowedType(pyClass, qname, narrowedType, original, !negated, typeIs)
    }

  companion object {
    fun create(anchor: PyElement, name: String, narrowedType: PyType, original: PyCallExpression, negated: Boolean = false, typeIs: Boolean): PyNarrowedType? {
      val pyClass = PyBuiltinCache.getInstance(anchor).getClass("bool")
      if (pyClass == null) return null
      return PyNarrowedType(pyClass, name, narrowedType, original, negated, typeIs)
    }
  }
}