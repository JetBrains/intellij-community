// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * PEP 800: Disjoint bases in the type system.
 *
 * Two classes with different, unrelated disjoint bases cannot have a common child class.
 */
@ApiStatus.Experimental
object PyDisjointBaseUtil {

  @JvmStatic
  fun areDisjoint(type1: PyClassType, type2: PyClassType, context: TypeEvalContext): Boolean {
    val class1 = type1.pyClass
    val class2 = type2.pyClass

    val disjointBase1 = findDisjointBase(class1, context) ?: return false
    val disjointBase2 = findDisjointBase(class2, context) ?: return false

    if (disjointBase1 == disjointBase2) return false
    if (disjointBase1.isSubclass(disjointBase2, context) || disjointBase2.isSubclass(disjointBase1, context)) return false

    return true
  }
  
  private fun findDisjointBase(pyClass: PyClass, context: TypeEvalContext): PyClass? {
    if (isDisjointBase(pyClass, context)) return pyClass

    val candidates = pyClass.getAncestorClasses(context).filter { isDisjointBase(it, context) }.toSet()

    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first()

    // Per PEP 800: if multiple disjoint base candidates exist, one must be a subclass of all others.
    return candidates.firstOrNull { candidate ->
      candidates.all { other -> candidate == other || candidate.isSubclass(other, context) }
    }
  }

  /**
   * Per PEP 800, a class is a disjoint base if:
   * - It has @disjoint_base decorator
   * - It has non-empty __slots__ (explicit or synthesized via @dataclass(slots=True) or dataclass_transform)
   */
  @JvmStatic
  fun isDisjointBase(pyClass: PyClass, context: TypeEvalContext): Boolean {
    return hasDisjointBaseDecorator(pyClass, context) || hasOwnNonEmptySlots(pyClass, context)
  }

  private fun hasDisjointBaseDecorator(pyClass: PyClass, context: TypeEvalContext): Boolean {
    return PyKnownDecoratorUtil.getKnownDecorators(pyClass, context).contains(PyKnownDecorator.DISJOINT_BASE_EXT)
  }

  private fun hasOwnNonEmptySlots(pyClass: PyClass, context: TypeEvalContext): Boolean {
    val ownSlots = pyClass.ownSlots
    if (!ownSlots.isNullOrEmpty()) return true

    val dataclassParams = parseDataclassParameters(pyClass, context)
    if (dataclassParams != null && dataclassParams.slots) return true

    return false
  }
}
