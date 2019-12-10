// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyTypedDictStub
import java.io.IOException
import java.util.*

class PyTypedDictStubImpl private constructor(private val myCalleeName: QualifiedName?,
                                              override val name: String,
                                              override val fields: LinkedHashMap<String, Optional<String>>,
                                              override val isTotal: Boolean = true) : PyTypedDictStub {

  override fun getTypeClass(): Class<out CustomTargetExpressionStubType<*>> {
    return PyTypedDictStubType::class.java
  }

  @Throws(IOException::class)
  override fun serialize(stream: StubOutputStream) {
    stream.writeName(myCalleeName?.toString())
    stream.writeName(name)
    stream.writeVarInt(fields.size)

    for ((key, value) in fields) {
      stream.writeName(key)
      stream.writeName(value.orElse(null))
    }
  }

  override fun getCalleeName(): QualifiedName? {
    return myCalleeName
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
        val name = PyResolveUtil.resolveStrArgument(expression, 0, "name") ?: return null

        val fieldsAndTotality = resolveTypingTDFields(expression)

        if (fieldsAndTotality?.first != null && fieldsAndTotality.second != null) {
          return PyTypedDictStubImpl(calleeName, name, fieldsAndTotality.first, fieldsAndTotality.second)
        }
      }

      return null
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyTypedDictStub? {
      val calleeName = stream.readNameString()
      val name = stream.readNameString()
      val fields = deserializeFields(stream, stream.readVarInt())

      return if (calleeName == null || name == null) {
        null
      }
      else PyTypedDictStubImpl(QualifiedName.fromDottedString(calleeName), name, fields)
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

    private fun resolveTypingTDFields(callExpression: PyCallExpression): Pair<LinkedHashMap<String, Optional<String>>, Boolean>? {
      // SUPPORTED CASES:

      // fields = {"x": str, "y": int}
      // Movie = TypedDict(..., fields)

      // Movie = TypedDict(..., {'name': str, 'year': int}, total=False)

      val secondArgument = PyPsiUtils.flattenParens(callExpression.getArgument(1, PyExpression::class.java))

      val resolvedFields = if (secondArgument is PyReferenceExpression) PyResolveUtil.fullResolveLocally(secondArgument) else secondArgument
      return if (resolvedFields !is PySequenceExpression) null
      else Pair.create(getTypingTDFieldsFromIterable(resolvedFields),
                       PyEvaluator.evaluateAsBoolean(callExpression.getKeywordArgument("total"), true))
    }

    private fun getTypingTDFieldsFromIterable(fields: PySequenceExpression): LinkedHashMap<String, Optional<String>>? {
      val result = LinkedHashMap<String, Optional<String>>()

      fields.elements.forEach {
        if (it !is PyKeyValueExpression) return null

        val name: PyExpression = it.key
        val type: PyExpression? = it.value

        if (name !is PyStringLiteralExpression) return null

        result[name.stringValue] = Optional.ofNullable(textIfPresent(type))
      }

      return result
    }

    private fun textIfPresent(element: PsiElement?): String? {
      return element?.text
    }
  }
}
