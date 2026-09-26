// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyTypingNewTypeStub
import java.io.IOException

class PyTypingNewTypeStubImpl private constructor(val qualifiedName: String, private val baseClassName: String) : PyTypingNewTypeStub {

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(qualifiedName)
    stream.writeName(baseClassName)
  }

  override fun getCalleeName(): QualifiedName {
    return QualifiedName.fromComponents(qualifiedName)
  }

  override fun getTypeClass(): Class<PyTypingNewTypeStubType> {
    return PyTypingNewTypeStubType::class.java
  }

  override fun getName(): String = qualifiedName

  override fun getClassType(): String = baseClassName

  override fun toString(): String {
    return "PyTypingNewTypeStub(qualifiedName='$qualifiedName', baseClass='$baseClassName')"
  }

  companion object {
    fun create(expression: PyTargetExpression): PyTypingNewTypeStub? {
      val assignedValue = expression.findAssignedValue()
      return if (assignedValue is PyCallExpression) create(assignedValue) else null
    }

    fun create(expression: PyCallExpression): PyTypingNewTypeStub? {
      if (isTypingNewType(expression)) {
        val newTypeName = PyResolveUtil.resolveStrArgument(expression, 0, "name")
        if (newTypeName != null) {
          val secondArgument = PyPsiUtils.flattenParens(expression.getArgument(1, "tp", PyExpression::class.java))
          if (secondArgument != null) {
            return PyTypingNewTypeStubImpl(newTypeName, secondArgument.text)
          }
        }
      }
      return null
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream?): PyTypingNewTypeStub? {
      if (stream != null) {
        val typeName = stream.readNameString()
        val classType = stream.readNameString()
        if (typeName != null && classType != null) {
          return PyTypingNewTypeStubImpl(typeName, classType)
        }
      }
      return null
    }

    private fun isTypingNewType(callExpression: PyCallExpression): Boolean {
      val callee = callExpression.callee as? PyReferenceExpression ?: return false
      return QualifiedName.fromDottedString(PyTypingTypeProvider.NEW_TYPE) in PyResolveUtil.resolveImportedElementQNameLocally(callee)
    }
  }
}