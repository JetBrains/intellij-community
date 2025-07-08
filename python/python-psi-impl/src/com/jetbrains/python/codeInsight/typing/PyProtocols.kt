// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL_EXT
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.getImplicitlyInvokedMethodTypes
import com.jetbrains.python.psi.impl.resolveImplicitlyInvokedMethods
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*


fun isProtocol(classLikeType: PyClassLikeType, context: TypeEvalContext): Boolean = containsProtocol(classLikeType.getSuperClassTypes(context))

fun isProtocol(cls: PyClass, context: TypeEvalContext): Boolean = containsProtocol(cls.getSuperClassTypes(context))

fun matchingProtocolDefinitions(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean = expected is PyClassLikeType &&
                                                                                                         actual is PyClassLikeType &&
                                                                                                         expected.isDefinition &&
                                                                                                         actual.isDefinition &&
                                                                                                         isProtocol(expected, context) &&
                                                                                                         isProtocol(actual, context)

typealias ProtocolAndSubclassElements = Pair<PyTypedElement, List<PyTypedResolveResult>>

fun inspectProtocolSubclass(protocol: PyClassType, subclass: PyClassType, context: TypeEvalContext): List<ProtocolAndSubclassElements> {
  val resolveContext = PyResolveContext.defaultContext(context)
  val result = mutableListOf<Pair<PyTypedElement, List<PyTypedResolveResult>>>()

  protocol.toInstance().visitMembers(
    { e ->
      if (e is PyTypedElement) {
        if (e is PyPossibleClassMember) {
          val cls = e.containingClass
          if (cls != null && !isProtocol(cls, context)) {
            return@visitMembers true
          }
        }
        if (e is PyTypeParameter) {
          return@visitMembers true
        }

        val name = e.name ?: return@visitMembers true
        when (name) {
          PyNames.CLASS_GETITEM -> return@visitMembers true
          PyNames.CALL -> {
            val types = subclass.getImplicitlyInvokedMethodTypes(null, resolveContext)
            if (types.isNotEmpty()) {
              result.add(Pair(e, types))
            }
            else {
              val fallbackTypes = subclass.resolveImplicitlyInvokedMethods(null, resolveContext)
                .mapNotNull { it.element }
                .filterIsInstance<PyTypedElement>()
                .mapNotNull {
                  val type = resolveContext.typeEvalContext.getType(it)
                  if (type != null) {
                    it to type
                  }
                  else {
                    null
                  }
                }
              result.add(Pair(e, fallbackTypes.map { PyTypedResolveResult(it.first, it.second) }))
            }
          }
          else -> {
            val types = subclass.getMemberTypes(name, null, AccessDirection.READ, resolveContext)
            if (types != null) {
              result.add(Pair(e, types))
            }
          }
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
