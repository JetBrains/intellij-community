// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.psi.util.contextOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL_EXT
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.getImplicitlyInvokedMethod
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

typealias ProtocolAndSubclassElements = Pair<PyTypeMember, List<PyTypeMember>>

fun inspectProtocolSubclass(protocol: PyClassType, subclass: PyClassType, context: TypeEvalContext): List<ProtocolAndSubclassElements> {
  val resolveContext = PyResolveContext.defaultContext(context)
  val result = mutableListOf<Pair<PyTypeMember, List<PyTypeMember>>>()

  val protocolMembers = protocol.toInstance().getAllMembers(resolveContext)
  val superClassesMembers = protocol.toInstance().getSuperClassTypes(context)
    .filter { isProtocol(it, context) }
    .flatMap { it.toInstance().getAllMembers(resolveContext).asIterable() }
  protocolMembers.addAll(superClassesMembers)

  for (protocolMember in protocolMembers) {
    val protocolElement = protocolMember.mainElement ?: continue
    if (protocolElement is PyPossibleClassMember) {
      val cls = protocolElement.containingClass
      if (cls != null && !isProtocol(cls, context)) {
        continue
      }
    }
    if (protocolElement is PyTypeParameter) {
      continue
    }

    if (protocolElement.contextOfType<PyFunction>()?.containingClass == protocol.pyClass) {
      continue
    }

    when (val name = protocolMember.name) {
      null -> continue
      PyNames.SLOTS -> continue // __slots__ in a protocol definition are not considered to be a part of the protocol
      PyNames.CLASS_GETITEM -> continue
      PyNames.CALL -> {
        val invokedMethods = subclass.getImplicitlyInvokedMethod(resolveContext)
        if (invokedMethods.isNotEmpty()) {
          result.add(Pair(protocolMember, invokedMethods))
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
          result.add(Pair(protocolMember, fallbackTypes.map { PyTypeMember(it.first, it.second) }))
        }
      }
      else -> {
        val subclassMembers = subclass.findMember(name, resolveContext)
        result.add(Pair(protocolMember, subclassMembers))
      }
    }
  }

  return result
}

private fun containsProtocol(types: List<PyClassLikeType?>) = types.any { type ->
  val classQName = type?.classQName
  PROTOCOL == classQName || PROTOCOL_EXT == classQName
}
