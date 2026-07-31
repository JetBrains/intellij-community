// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.impl.PyBuiltinCache
import org.jetbrains.annotations.ApiStatus

/**
 * The PEP 747 `typing.TypeForm` special form: a value that denotes the type [representedType].
 *
 * Backed by the builtin `type` class; matching and inference semantics live in [PyTypeChecker] and
 * [PyTypeInferenceCspFactory].
 */
@ApiStatus.Internal
class PyTypeFormType private constructor(
  pyClass: PyClass,
  val representedType: PyType?,
) : PyClassTypeImpl(pyClass, false) {

  fun substitute(representedTypeSubstitution: PyType?): PyTypeFormType {
    return PyTypeFormType(pyClass, representedTypeSubstitution)
  }

  // Preserve identity through conversions instead of collapsing to the backing `type` class.
  override fun toInstance(): PyClassType = this

  override fun toClass(): PyClassType = this

  override fun toString(): String = "PyTypeFormType: ${representedType?.name ?: "Any"}"

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (javaClass != o?.javaClass) return false
    if (!super.equals(o)) return false

    o as PyTypeFormType

    return representedType == o.representedType
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + (representedType?.hashCode() ?: 0)
    return result
  }

  override fun <T> acceptTypeVisitor(visitor: PyTypeVisitor<T>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyTypeFormType(this)
    }
    return visitor.visitPyClassType(this)
  }

  companion object {
    @JvmStatic
    fun create(anchor: PsiElement, representedType: PyType?): PyTypeFormType? {
      val pyClass = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TYPE) ?: return null
      return PyTypeFormType(pyClass, representedType)
    }

    // Erased runtime containers of `X | Y` value expressions; ignored when reading a union's represented type.
    private val RUNTIME_TYPE_FORM_CONTAINERS: Set<String> = setOf("types.UnionType", "types.GenericAlias")

    /**
     * The type [type] denotes as a type-expression value (`type[int]` -> `int`), or `null` if it is
     * not a valid type expression.
     */
    @JvmStatic
    fun representedTypeOf(type: PyType?): PyType? {
      if (type is PyTypeFormType) return type.representedType
      // `None` is both a value and a type expression denoting `NoneType`.
      if (type.isNoneType) return type
      if (type is PyInstantiableType<*> && type.isDefinition) return type.toInstance()
      if (type is PyUnionType) {
        val represented = mutableListOf<PyType?>()
        for (member in type.members) {
          when {
            member is PyInstantiableType<*> && member.isDefinition -> represented.add(member.toInstance())
            member.isNoneType -> represented.add(member)
            member is PyClassType && member.classQName in RUNTIME_TYPE_FORM_CONTAINERS -> {} // erased container, ignore
            else -> return null
          }
        }
        return if (represented.isEmpty()) null else PyUnionType.union(represented)
      }
      return null
    }
  }
}
