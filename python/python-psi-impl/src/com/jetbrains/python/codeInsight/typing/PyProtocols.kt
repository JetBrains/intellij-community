// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.psi.util.contextOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKnownDecorator.TYPING_RUNTIME
import com.jetbrains.python.psi.PyKnownDecorator.TYPING_RUNTIME_CHECKABLE
import com.jetbrains.python.psi.PyKnownDecorator.TYPING_RUNTIME_CHECKABLE_EXT
import com.jetbrains.python.psi.PyKnownDecorator.TYPING_RUNTIME_EXT
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyPossibleClassMember
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeMember
import com.jetbrains.python.psi.types.TypeEvalContext


fun PyClassLikeType.isProtocol(context: TypeEvalContext): Boolean = containsProtocol(getSuperClassTypes(context))

fun PyClass.isProtocol(context: TypeEvalContext): Boolean = containsProtocol(getSuperClassTypes(context))

fun PyClassType.isRuntimeCheckable(context: TypeEvalContext): Boolean =
  PyKnownDecoratorUtil.getKnownDecorators(pyClass, context).any {
    it in listOf(TYPING_RUNTIME_CHECKABLE, TYPING_RUNTIME_CHECKABLE_EXT, TYPING_RUNTIME, TYPING_RUNTIME_EXT)
  }

fun matchingProtocolDefinitions(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean = expected is PyClassLikeType &&
                                                                                                         actual is PyClassLikeType &&
                                                                                                         expected.isDefinition &&
                                                                                                         actual.isDefinition &&
                                                                                                         expected.isProtocol(context) &&
                                                                                                         actual.isProtocol(context)

typealias ProtocolAndSubclassElements = Pair<PyTypeMember, List<PyTypeMember>>

fun inspectProtocolSubclass(protocol: PyClassType, subclass: PyClassType, context: TypeEvalContext): List<ProtocolAndSubclassElements> {
  val resolveContext = PyResolveContext.defaultContext(context)
  val result = mutableListOf<Pair<PyTypeMember, List<PyTypeMember>>>()

  val protocolMembers = protocol.getProtocolMembers(context)

  for (protocolMember in protocolMembers) {
    val protocolElement = protocolMember.element ?: continue
    if (protocolElement.contextOfType<PyFunction>()?.containingClass == protocol.pyClass) {
      continue
    }

    when (val name = protocolMember.name) {
      null -> continue
      PyNames.CALL -> {
        val invokedMethods = PyCallExpressionHelper.getImplicitlyInvokedMethod(subclass, resolveContext)
        if (invokedMethods.isNotEmpty()) {
          result.add(Pair(protocolMember, invokedMethods))
        }
        else {
          val fallbackTypes = PyCallExpressionHelper.resolveImplicitlyInvokedMethods(subclass, null, resolveContext)
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

fun PyClassLikeType.getProtocolMembers(context: TypeEvalContext): List<PyTypeMember> {
  val resolveContext = PyResolveContext.defaultContext(context)
  val result = mutableListOf<PyTypeMember>()

  val superClassesMembers = toInstance().getSuperClassTypes(context)
    .filterNotNull()
    .filter { it.isProtocol(context) }
    .flatMap { it.toInstance().getAllMembers(resolveContext).asIterable() }
  val protocolMembers = toInstance().getAllMembers(resolveContext) + superClassesMembers


  for (protocolMember in protocolMembers) {
    val protocolElement = protocolMember.element ?: continue
    if (protocolElement is PyPossibleClassMember) {
      val cls = protocolElement.containingClass
      if (cls != null && !cls.isProtocol(context)) {
        continue
      }
    }
    if (protocolElement is PyTypeParameter) {
      continue
    }

    val name = protocolMember.name
    if (name == null) continue
    if (name in ignoredNamesForProtocolMatching) continue
    result.add(protocolMember)
  }

  return result
}

private fun containsProtocol(types: List<PyClassLikeType?>) = types.any { type ->
  val classQName = type?.classQName
  PyTypingTypeProvider.PROTOCOL == classQName || PyTypingTypeProvider.PROTOCOL_EXT == classQName
}

private val ignoredNamesForProtocolMatching =
  setOf(PyNames.CLASS_GETITEM, PyNames.SLOTS, PyNames.NAME, PyNames.QUALNAME, PyNames.MODULE, PyNames.ANNOTATIONS)
