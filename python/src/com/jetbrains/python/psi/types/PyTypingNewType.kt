// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.PyResolveUtil

data class PyTypingNewType(internal val classType: PyClassType, internal val isDefinition: Boolean, internal val myName: String?) : PyClassTypeImpl(
  classType.pyClass, isDefinition) {

  override fun getName(): String? = myName

  override fun getCallType(context: TypeEvalContext, callSite: PyCallSiteExpression): PyType? {
    return PyTypingNewType(classType, false, name)
  }

  override fun toClass(): PyClassLikeType {
    return if (isDefinition) this else PyTypingNewType(classType, true, name)
  }

  override fun toInstance(): PyClassType {
    return if (isDefinition) PyTypingNewType(classType, false, name) else this
  }

  override fun isBuiltin(): Boolean = false

  override fun isCallable(): Boolean = classType.isCallable || isDefinition

  override fun toString(): String = "TypingNewType: $myName"

  override fun getParameters(context: TypeEvalContext): List<PyCallableParameter>? {
    return if (isCallable) {
      listOf(PyCallableParameterImpl.nonPsi(null, classType.toInstance(), null))
    }
    else {
      null
    }
  }

  override fun getSuperClassTypes(context: TypeEvalContext): List<PyClassLikeType> = listOf(classType)

  override fun getAncestorTypes(context: TypeEvalContext): List<PyClassLikeType> {
    return listOf(classType) + classType.getAncestorTypes(context)
  }

  companion object {
    fun isTypingNewType(callExpression: PyCallExpression): Boolean {
      val callee = callExpression.callee as? PyReferenceExpression ?: return false
      return QualifiedName.fromDottedString(PyTypingTypeProvider.NEW_TYPE) in PyResolveUtil.resolveImportedElementQNameLocally(callee)
    }
  }
}
