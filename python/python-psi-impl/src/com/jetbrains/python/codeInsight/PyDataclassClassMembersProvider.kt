// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassMembersProviderBase
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.TypeEvalContext

private enum class ProvidedDataclassMember(val memberName: String) {
  DUNDER_SLOTS(PyDataclassNames.Dataclasses.DUNDER_SLOTS),
  DUNDER_ATTRS(PyDataclassNames.Attrs.DUNDER_ATTRS),
  DUNDER_LT(PyNames.LT),
  DUNDER_LE(PyNames.LE),
  DUNDER_GT(PyNames.GT),
  DUNDER_GE(PyNames.GE);

  val key: Key<CachedValue<PyCustomMember?>> = Key.create("py.dataclass.member.$memberName")

  companion object {
    private val byName = entries.associateBy { it.memberName }

    fun fromName(name: String): ProvidedDataclassMember? = byName[name]
  }
}

/**
 * Adds members for dataclass-like classes.
 */
class PyDataclassClassMembersProvider : PyClassMembersProviderBase() {

  override fun resolveMember(type: PyClassType, name: String, location: PsiElement?, resolveContext: PyResolveContext): PsiElement? {
    val pyClass = type.pyClass
    val context = resolveContext.typeEvalContext
    val member = ProvidedDataclassMember.fromName(name) ?: return null
    return getCachedCustomMember(type, member, context)?.resolve(pyClass, resolveContext)
  }

  override fun getMembers(clazz: PyClassType, location: PsiElement?, context: TypeEvalContext): Collection<PyCustomMember> =
    ProvidedDataclassMember.entries.mapNotNull { member ->
      getCachedCustomMember(clazz, member, context)
    }

  private fun getCachedCustomMember(type: PyClassType, member: ProvidedDataclassMember, context: TypeEvalContext): PyCustomMember? =
    CachedValuesManager.getCachedValue(type.pyClass, member.key) {
      val customMember = getCustomMember(type, member, context)
      CachedValueProvider.Result.create(customMember, PsiModificationTracker.MODIFICATION_COUNT)
    }

  private fun getCustomMember(type: PyClassType, member: ProvidedDataclassMember, context: TypeEvalContext): PyCustomMember? {
    val pyClass = type.pyClass
    val dataclassParameters = parseDataclassParameters(pyClass, context)
    return when (member) {

      // Member __slots__ caused by dataclass.slots
      ProvidedDataclassMember.DUNDER_SLOTS -> {
        if (dataclassParameters?.slots != true) return null

        val strType = PyBuiltinCache.getInstance(pyClass).strType ?: return null
        // creating the following PyTupleType requires caching the result to avoid Idempotency Errors at runtime
        val tupleOfStrings = PyTupleType.createHomogeneous(pyClass, strType) ?: return null
        val qNameTuple = tupleOfStrings.pyClass.qualifiedName
        PyCustomMember(member.memberName, qNameTuple) { tupleOfStrings }
      }

      // Member __attrs_attrs__ caused by decorator @attrs.define
      ProvidedDataclassMember.DUNDER_ATTRS -> {
        if (dataclassParameters?.type != PyDataclassParameters.PredefinedType.ATTRS) return null

        val objectClass = PyBuiltinCache.getInstance(pyClass).getClass(PyNames.OBJECT)
        PyCustomMember(member.memberName, objectClass).asClassVar()
      }

      // Members __lt__, __le__, __gt__, __ge__ caused by @dataclass(order=True)
      ProvidedDataclassMember.DUNDER_LT,
      ProvidedDataclassMember.DUNDER_LE,
      ProvidedDataclassMember.DUNDER_GT,
      ProvidedDataclassMember.DUNDER_GE,
        -> {
        if (dataclassParameters?.order != true) return null
        if (pyClass.findMethodByName(member.memberName, false, context) != null) return null

        val boolType = PyBuiltinCache.getInstance(pyClass).boolType ?: return null
        PyCustomMember(member.memberName, null) {
          PyCallableTypeImpl(listOf(PyCallableParameterImpl.nonPsi("other", type.toInstance())), boolType)
        }.toPsiElement(pyClass)
      }
    }
  }
}
