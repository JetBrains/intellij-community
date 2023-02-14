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
import com.jetbrains.python.psi.stubs.PyTypedDictStub
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_FIELDS_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_NAME_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_TOTAL_PARAMETER
import java.io.IOException
import java.util.*

class PyTypedDictStubImpl private constructor(private val myCalleeName: QualifiedName,
                                              override val name: String,
                                              override val fields: LinkedHashMap<String, Optional<String>>,
                                              override val isRequired: Boolean = true) : PyTypedDictStub {

  override fun getTypeClass(): Class<out CustomTargetExpressionStubType<*>> {
    return PyTypedDictStubType::class.java
  }

  @Throws(IOException::class)
  override fun serialize(stream: StubOutputStream) {
    stream.writeName(myCalleeName.toString())
    stream.writeName(name)
    stream.writeBoolean(isRequired)
    stream.writeVarInt(fields.size)

    for ((key, value) in fields) {
      stream.writeName(key)
      stream.writeName(value.orElse(null))
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

      val calleeName = getCalleeName(calleeReference)

      if (calleeName != null) {
        val name = PyResolveUtil.resolveStrArgument(expression, 0, TYPED_DICT_NAME_PARAMETER) ?: return null

        val fieldsArgument = expression.getArgument(1, TYPED_DICT_FIELDS_PARAMETER, PyDictLiteralExpression::class.java) ?: return null

        val fields = getTypingTDFieldsFromIterable(fieldsArgument)
        if (fields != null) {
          return PyTypedDictStubImpl(calleeName,
                                     name,
                                     fields,
                                     PyEvaluator.evaluateAsBoolean(expression.getKeywordArgument(TYPED_DICT_TOTAL_PARAMETER), true))
        }
      }

      return null
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
    private fun deserializeFields(stream: StubInputStream, fieldsSize: Int): LinkedHashMap<String, Optional<String>> {
      val fields = LinkedHashMap<String, Optional<String>>(fieldsSize)

      for (i in 0 until fieldsSize) {
        val name = stream.readNameString()
        val type = stream.readNameString()

        if (name != null) {
          fields[name] = Optional.ofNullable(type)
        }
      }

      return fields
    }

    private fun getTypingTDFieldsFromIterable(fields: PySequenceExpression): LinkedHashMap<String, Optional<String>>? {
      val result = LinkedHashMap<String, Optional<String>>()

      fields.elements.forEach {
        if (it !is PyKeyValueExpression) return null

        val name: PyExpression = it.key
        val type: PyExpression? = it.value

        if (name !is PyStringLiteralExpression) return null

        result[name.stringValue] = Optional.ofNullable(type?.text)
      }

      return result
    }
  }
}
