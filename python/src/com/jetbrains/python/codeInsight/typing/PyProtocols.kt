// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.isNullOrEmpty
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.PROTOCOL_EXT
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext


fun isProtocol(classLikeType: PyClassLikeType, context: TypeEvalContext) = containsProtocol(classLikeType.getSuperClassTypes(context))

fun isProtocol(cls: PyClass, context: TypeEvalContext) = containsProtocol(cls.getSuperClassTypes(context))

fun matchingProtocolDefinitions(expected: PyType?, actual: PyType?, context: TypeEvalContext) = expected is PyClassLikeType &&
                                                                                                actual is PyClassLikeType &&
                                                                                                expected.isDefinition &&
                                                                                                actual.isDefinition &&
                                                                                                isProtocol(expected, context) &&
                                                                                                isProtocol(actual, context)

fun inspectProtocolSubclass(protocol: PyClassType,
                            subclass: PyClassType,
                            context: TypeEvalContext,
                            callback: InspectingProtocolSubclassCallback) {
  val subclassAsInstance = subclass.toInstance()
  val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
  var result = true

  protocol.toInstance().visitMembers(
    { e ->
      if (FileUtil.getNameWithoutExtension(e.containingFile.name) == "typing_extensions") {
        return@visitMembers result
      }

      if (result && e is PyTypedElement) {
        val name = e.name ?: return@visitMembers result
        val resolveResults = subclassAsInstance.resolveMember(name, null, AccessDirection.READ, resolveContext)

        result = if (resolveResults.isNullOrEmpty()) callback.onUnresolved(e) else callback.onResolved(e, resolveResults!!)
      }

      result
    },
    true,
    context
  )
}

interface InspectingProtocolSubclassCallback {
  fun onUnresolved(protocolElement: PyTypedElement): Boolean
  fun onResolved(protocolElement: PyTypedElement, subclassElements: List<RatedResolveResult>): Boolean
}

private fun containsProtocol(types: List<PyClassLikeType?>) = types.any { type ->
  val classQName = type?.classQName
  PROTOCOL == classQName || PROTOCOL_EXT == classQName
}