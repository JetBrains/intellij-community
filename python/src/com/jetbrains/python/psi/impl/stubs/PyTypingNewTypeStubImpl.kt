// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyTypingNewTypeStub
import com.jetbrains.python.psi.types.PyTypingNewType
import java.io.IOException

class PyTypingNewTypeStubImpl private constructor(val qualifiedName: String,
                                                  private val baseClassName: String) : PyTypingNewTypeStub {

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(qualifiedName)
    stream.writeName(baseClassName)
  }

  override fun getCalleeName(): QualifiedName? {
    return QualifiedName.fromComponents(qualifiedName)
  }

  override fun getTypeClass(): Class<out CustomTargetExpressionStubType<out CustomTargetExpressionStub>> {
    return PyTypingNewTypeStubType::class.java
  }

  companion object {
    fun create(expression: PyTargetExpression?): PyTypingNewTypeStub? {
      val callExpression = expression?.findAssignedValue() as? PyCallExpression ?: return null

      if (PyTypingNewType.isTypingNewType(callExpression)) {
        val newTypeName = PyResolveUtil.resolveFirstStrArgument(callExpression)
        if (newTypeName != null) {
          val secondArgument = PyPsiUtils.flattenParens(callExpression.getArgument(1, PyExpression::class.java))
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
  }

  override fun getName(): String = qualifiedName

  override fun getClassType(): String = baseClassName


}