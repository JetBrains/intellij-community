// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyClass

sealed class PyFixtureRequestType(
  private val name: String,
  private val pyClass: PyClass,
) : PyClassTypeImpl(pyClass, false) {

  override fun getName(): String = "_pytest.fixtures.FixtureRequest"
  override fun isBuiltin(): Boolean = false
  override fun isCallable(): Boolean = false

  override fun getCallType(context: TypeEvalContext, callSite: PyCallSiteExpression): PyType? = null

  override fun toString(): String = "PyRequestType: $name"

  class TopRequest(pyClass: PyClass) : PyFixtureRequestType("TopRequest", pyClass) {
    override fun <T : Any?> acceptTypeVisitor(visitor: PyTypeVisitor<T?>): T? {
      return if (visitor is PyTypeVisitorExt) {
        visitor.visitPyClassType(this)
      }
      else {
        visitor.visitPyClassType(this)
      }
    }
  }

  class SubRequest(pyClass: PyClass) : PyFixtureRequestType("SubRequest", pyClass) {
    override fun <T : Any?> acceptTypeVisitor(visitor: PyTypeVisitor<T?>): T? {
      return if (visitor is PyTypeVisitorExt) {
        visitor.visitPyClassType(this)
      }
      else {
        visitor.visitPyClassType(this)
      }
    }
  }

  companion object {
    fun createTopRequest(project: PyClass): TopRequest {
      return TopRequest(project)
    }

    fun createSubRequest(project: PyClass): SubRequest {
      return SubRequest(project)
    }
  }
}