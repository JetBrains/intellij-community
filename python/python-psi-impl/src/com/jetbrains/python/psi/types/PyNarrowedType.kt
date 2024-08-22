package com.jetbrains.python.psi.types

import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import org.jetbrains.annotations.ApiStatus

/**
 * Class is used for representing TypeGuard and TypeIs behavior
 */
@ApiStatus.Internal
class PyNarrowedType private constructor(
  pyClass: PyClass,
  val qname: String?,
  val original: PyCallSiteExpression?,
  val negated: Boolean,
  val typeIs: Boolean,
  typeVars: List<PyType>,
)
  : PyCollectionTypeImpl(pyClass, false, typeVars) {

    fun negate(): PyNarrowedType {
      return PyNarrowedType(pyClass, qname, original, !negated, typeIs, myElementTypes)
    }

    fun bind(callExpression: PyCallSiteExpression, name: String): PyNarrowedType {
      return PyNarrowedType(pyClass, name, callExpression, negated, typeIs, myElementTypes)
    }

    fun substitute(type: List<PyType>): PyNarrowedType {
      return PyNarrowedType(pyClass, qname, original, negated, typeIs, type)
    }

  /**
   * A type is considered bound if it has an associated original call site expression,
   * indicating that it is valid only within the call-side scope.
   */
  fun isBound(): Boolean = original != null

  val narrowedType: PyType
    get() = requireNotNull(iteratedItemType)

  companion object {

    private val myTypeVar = object : PyGenericType(toString(), null) {}

    fun create(anchor: PyElement, typeIs: Boolean): PyNarrowedType? {
      val pyClass = PyBuiltinCache.getInstance(anchor).getClass("bool")
      if (pyClass == null) return null
      return PyNarrowedType(pyClass, null, null, false, typeIs, listOf(myTypeVar))
    }

    fun bindIfNeeded(type: PyType?, callSiteExpression: PyCallSiteExpression?): PyType? {
      if (type is PyNarrowedType && callSiteExpression != null) {
        val arguments = callSiteExpression.getArguments(null)
        val pyReferenceExpression = arguments.firstOrNull()
        if (pyReferenceExpression is PyReferenceExpression) {
          val qname = PyPsiUtilsCore.asQualifiedName(pyReferenceExpression)
          if (qname != null) {
            return type.bind(callSiteExpression, qname.toString())
          }
        }
      }
      return type
    }
  }
}