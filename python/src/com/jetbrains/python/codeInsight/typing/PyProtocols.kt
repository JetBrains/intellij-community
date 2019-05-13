// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL_EXT
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyPossibleClassMember
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext


fun isProtocol(classLikeType: PyClassLikeType, context: TypeEvalContext): Boolean = containsProtocol(classLikeType.getSuperClassTypes(context))

fun isProtocol(cls: PyClass, context: TypeEvalContext): Boolean = containsProtocol(cls.getSuperClassTypes(context))

fun matchingProtocolDefinitions(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean = expected is PyClassLikeType &&
                                                                                                         actual is PyClassLikeType &&
                                                                                                         expected.isDefinition &&
                                                                                                         actual.isDefinition &&
                                                                                                         isProtocol(expected, context) &&
                                                                                                         isProtocol(actual, context)

typealias ProtocolAndSubclassElements = Pair<PyTypedElement, List<RatedResolveResult>?>

fun inspectProtocolSubclass(protocol: PyClassType, subclass: PyClassType, context: TypeEvalContext): List<ProtocolAndSubclassElements> {
  val subclassAsInstance = subclass.toInstance()
  val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
  val result = mutableListOf<Pair<PyTypedElement, List<RatedResolveResult>?>>()

  protocol.toInstance().visitMembers(
    { e ->
      if (e is PyTypedElement) {
        if (e is PyPossibleClassMember) {
          val cls = e.containingClass
          if (cls != null && !isProtocol(cls, context)) {
            return@visitMembers true
          }
        }

        val name = e.name ?: return@visitMembers true
        val resolveResults = subclassAsInstance.resolveMember(name, null, AccessDirection.READ, resolveContext)

        result.add(Pair(e, resolveResults))
      }

      true
    },
    true,
    context
  )

  return result
}

private fun containsProtocol(types: List<PyClassLikeType?>) = types.any { type ->
  val classQName = type?.classQName
  PROTOCOL == classQName || PROTOCOL_EXT == classQName
}