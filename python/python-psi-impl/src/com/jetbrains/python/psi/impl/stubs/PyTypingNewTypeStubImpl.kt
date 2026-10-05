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

class PyTypingNewTypeStubImpl private constructor(
  val qualifiedName: String,
  private val baseClassName: String,
  private val unresolvedCalleeName: String?,
) : PyTypingNewTypeStub {

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(qualifiedName)
    stream.writeName(baseClassName)
    stream.writeName(unresolvedCalleeName)
  }

  override fun getCalleeName(): QualifiedName {
    return QualifiedName.fromComponents(qualifiedName)
  }

  override fun getTypeClass(): Class<PyTypingNewTypeStubType> {
    return PyTypingNewTypeStubType::class.java
  }

  override fun getName(): String = qualifiedName

  override fun getClassType(): String = baseClassName

  override fun getUnresolvedCalleeName(): String? = unresolvedCalleeName

  override fun toString(): String {
    return "PyTypingNewTypeStub(qualifiedName='$qualifiedName', baseClass='$baseClassName', unresolvedCallee=$unresolvedCalleeName)"
  }

  companion object {
    fun create(expression: PyTargetExpression): PyTypingNewTypeStub? {
      val assignedValue = expression.findAssignedValue()
      return if (assignedValue is PyCallExpression) create(assignedValue) else null
    }

    fun create(expression: PyCallExpression): PyTypingNewTypeStub? {
      val callee = expression.callee as? PyReferenceExpression ?: return null
      val unresolvedCalleeName = when {
        isTypingNewType(callee) -> null
        // The callee may be a re-export of `typing.NewType` from another module, which stubs cannot resolve
        callee.referencedName == NEW_TYPE_NAME -> callee.asQualifiedName()?.toString() ?: return null
        else -> return null
      }
      val newTypeName = PyResolveUtil.resolveStrArgument(expression, 0, "name") ?: return null
      val secondArgument = PyPsiUtils.flattenParens(expression.getArgument(1, "tp", PyExpression::class.java)) ?: return null
      return PyTypingNewTypeStubImpl(newTypeName, secondArgument.text, unresolvedCalleeName)
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream?): PyTypingNewTypeStub? {
      if (stream != null) {
        val typeName = stream.readNameString()
        val classType = stream.readNameString()
        val unresolvedCalleeName = stream.readNameString()
        if (typeName != null && classType != null) {
          return PyTypingNewTypeStubImpl(typeName, classType, unresolvedCalleeName)
        }
      }
      return null
    }

    private const val NEW_TYPE_NAME = "NewType"

    private fun isTypingNewType(callee: PyReferenceExpression): Boolean {
      return PyResolveUtil.resolveImportedElementQNameLocally(callee).any {
        it.toString() == PyTypingTypeProvider.NEW_TYPE || it.toString() == PyTypingTypeProvider.NEW_TYPE_EXT
      }
    }
  }
}