// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyTypedDictFieldStub
import com.jetbrains.python.psi.stubs.PyTypedDictStub
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_TOTAL_PARAMETER
import java.io.IOException
import java.util.*

class PyTypedDictStubImpl private constructor(private val myCalleeName: QualifiedName,
                                              override val name: String,
                                              override val fields: List<PyTypedDictFieldStub>,
                                              override val isRequired: Boolean = true) : PyTypedDictStub {

  override fun getTypeClass(): Class<PyTypedDictStubType> {
    return PyTypedDictStubType::class.java
  }

  @Throws(IOException::class)
  override fun serialize(stream: StubOutputStream) {
    stream.writeName(myCalleeName.toString())
    stream.writeName(name)
    stream.writeBoolean(isRequired)
    stream.writeVarInt(fields.size)

    for ((name, type, isReadOnly) in fields) {
      stream.writeName(name)
      stream.writeName(type)
      stream.writeBoolean(isReadOnly)
    }
  }

  override fun getCalleeName(): QualifiedName {
    return myCalleeName
  }

  override fun toString(): String {
    return "PyTypedDictStub(calleeName=$myCalleeName, name=$name, fields=$fields, isRequired=$isRequired)"
  }

  companion object {

    fun create(expression: PyTargetExpression): PyTypedDictStub? {
      val assignedValue = expression.findAssignedValue()

      return if (assignedValue is PyCallExpression) create(assignedValue) else null
    }

    fun create(expression: PyCallExpression): PyTypedDictStub? {
      val calleeReference = expression.callee as? PyReferenceExpression ?: return null
      val calleeName = getCalleeName(calleeReference) ?: return null

      val arguments = expression.arguments
      val typeName = PyResolveUtil.resolveStrArgument(arguments.getOrNull(0)) ?: return null

      val fieldsArg = PyPsiUtils.flattenParens(arguments.getOrNull(1))
      val fields = if (fieldsArg is PyDictLiteralExpression) getTypedDictFieldsFromDictLiteral(fieldsArg) else emptyList()

      return PyTypedDictStubImpl(calleeName,
                                 typeName,
                                 fields,
                                 PyEvaluator.evaluateAsBoolean(expression.getKeywordArgument(TYPED_DICT_TOTAL_PARAMETER), true))
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyTypedDictStub? {
      val calleeName = stream.readNameString()
      val name = stream.readNameString()
      val isRequired = stream.readBoolean()
      val fields = deserializeFields(stream, stream.readVarInt())

      return if (calleeName == null || name == null) {
        null
      }
      else PyTypedDictStubImpl(QualifiedName.fromDottedString(calleeName), name, fields, isRequired)
    }

    private fun getCalleeName(referenceExpression: PyReferenceExpression): QualifiedName? {
      val calleeName = PyPsiUtils.asQualifiedName(referenceExpression) ?: return null

      for (name in PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression).map { it.toString() }) {
        if (PyTypedDictTypeProvider.nameIsTypedDict(name)) {
          return calleeName
        }
      }

      return null
    }

    @Throws(IOException::class)
    private fun deserializeFields(stream: StubInputStream, fieldsCount: Int): List<PyTypedDictFieldStub> {
      val fields =  ArrayList<PyTypedDictFieldStub>(fieldsCount)

      for (i in 0 until fieldsCount) {
        val name = stream.readNameString()
        val type = stream.readNameString()
        val readOnly = stream.readBoolean()

        if (name != null) {
          fields.add(PyTypedDictFieldStub(name, type, readOnly))
        }
      }

      return fields
    }

    private fun getTypedDictFieldsFromDictLiteral(expression: PyDictLiteralExpression): List<PyTypedDictFieldStub> {
      val result = mutableListOf<PyTypedDictFieldStub>()
      expression.elements.forEach {
        val key = it.key
        if (key is PyStringLiteralExpression) {
          result.add(PyTypedDictFieldStub(key.stringValue, it.value?.text, true))
        }
      }
      return result
    }
  }
}
