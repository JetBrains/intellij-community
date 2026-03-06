// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyDataclassNames.Dataclasses
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyClassMembersProviderBase
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Adds members for dataclass-like classes.
 */
class PyDataclassClassMembersProvider : PyClassMembersProviderBase() {

  override fun getMembers(
    clazz: PyClassType,
    location: PsiElement?,
    context: TypeEvalContext,
  ): Collection<PyCustomMember> {
    val pyClass = clazz.pyClass
    val dataclassParameters = parseDataclassParameters(pyClass, context)

    return CachedValuesManager.getCachedValue(pyClass) {
      val result = mutableListOf<PyCustomMember>()

      // Adds member __slots__ caused by dataclass.slots
      if (dataclassParameters?.slots == true) {
        val strType = PyBuiltinCache.getInstance(pyClass).strType
        if (strType != null) {
          // creating the following PyTupleType requires caching the result to avoid Idempotency Errors at runtime
          val tupleOfStrings = PyTupleType.createHomogeneous(pyClass, strType)
          if (tupleOfStrings != null) {
            val qNameTuple = tupleOfStrings.pyClass.qualifiedName
            result.add(PyCustomMember(Dataclasses.DUNDER_SLOTS, qNameTuple) { tupleOfStrings })
          }
        }
      }

      // Adds member __attrs_attrs__ caused by decorator @attrs.define
      val hasAttrs = dataclassParameters?.type == PyDataclassParameters.PredefinedType.ATTRS
      if (hasAttrs) {
        val objectClass = PyBuiltinCache.getInstance(pyClass).getClass(PyNames.OBJECT)
        result.add(PyCustomMember("__attrs_attrs__", objectClass).asClassVar())
      }

      CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
    }
  }
}
