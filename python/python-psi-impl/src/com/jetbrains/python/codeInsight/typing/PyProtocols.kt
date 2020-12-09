// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL_EXT
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionHelper.resolveImplicitlyInvokedMethods
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.*


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
  val resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context)
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

        if (name == PyNames.CALL) {
          result.add(Pair(e, resolveImplicitlyInvokedMethods(subclass, null, resolveContext)))
        }
        else {
          result.add(Pair(e, subclass.resolveMember(name, null, AccessDirection.READ, resolveContext)))
        }

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
