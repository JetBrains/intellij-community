// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult

class PyTypingNewType(private val classType: PyClassType,
                      private val name: String,
                      private val declaration: PyTargetExpression?) : PyClassType by classType {

  override fun getName(): String = name

  override fun getCallType(context: TypeEvalContext, callSite: PyCallSiteExpression): PyType? {
    val instance = classType.toInstance()
    return if (instance is PyClassType) {
      PyTypingNewType(instance, name, declaration)
    }
    else {
      classType.getCallType(context, callSite)
    }
  }

  override fun toClass(): PyClassLikeType {
    return if (isDefinition) this
    else {
      val definition = classType.toClass()
      if (definition is PyClassType) PyTypingNewType(definition, name, declaration) else definition
    }
  }

  override fun toInstance(): PyClassLikeType {
    return if (isDefinition) {
      val instance = classType.toInstance()
      if (instance is PyClassType) PyTypingNewType(instance, name, declaration) else instance
    }
    else this
  }

  override fun isBuiltin(): Boolean = false

  override fun isCallable(): Boolean = classType.isCallable || isDefinition

  override fun toString(): String = "TypingNewType: $name"

  override fun getParameters(context: TypeEvalContext): List<PyCallableParameter>? {
    return if (isCallable) {
      listOf(PyCallableParameterImpl.nonPsi(null, classType.toInstance(), null))
    }
    else {
      null
    }
  }

  override fun getSuperClassTypes(context: TypeEvalContext): List<PyClassLikeType> = listOf(classType)

  override fun resolveMember(
    name: String, location: PyExpression?, direction: AccessDirection, resolveContext: PyResolveContext,
    inherited: Boolean,
  ): MutableList<out RatedResolveResult>? {
    return if (name == PyNames.CLASS_GETITEM) {
      mutableListOf()
    }
    else {
      classType.resolveMember(name, location, direction, resolveContext, inherited)
    }
  }

  override fun resolveMember(name: String, location: PyExpression?, direction: AccessDirection, resolveContext: PyResolveContext)
    : MutableList<out RatedResolveResult>? {
    return if (name == PyNames.CLASS_GETITEM) {
      mutableListOf()
    }
    else {
      classType.resolveMember(name, location, direction, resolveContext)
    }
  }

  override fun getAncestorTypes(context: TypeEvalContext): List<PyClassLikeType> {
    return listOf(classType) + classType.getAncestorTypes(context)
  }

  override fun getDeclarationElement(): PyQualifiedNameOwner? = declaration ?: classType.declarationElement

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PyTypingNewType

    if (classType != other.classType) return false
    if (name != other.name) return false
    if (declaration != null && other.declaration != null && declaration != other.declaration) return false

    return true
  }

  override fun hashCode(): Int {
    return 31 * classType.hashCode() + name.hashCode()
  }

  override fun <T : Any?> acceptTypeVisitor(visitor: PyTypeVisitor<T?>): T? {
    if (visitor is PyTypeVisitorExt) {
      return visitor.visitPyTypingNewType(this)
    }
    return visitor.visitPyClassType(this)
  }
}
