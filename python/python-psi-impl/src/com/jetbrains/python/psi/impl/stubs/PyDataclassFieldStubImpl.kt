/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.resolvesToOmittedDefault
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import java.io.IOException

class PyDataclassFieldStubImpl private constructor(private val calleeName: QualifiedName,
                                                   private val parameters: FieldParameters) : PyDataclassFieldStub {
  companion object {
    fun create(expression: PyTargetExpression): PyDataclassFieldStub? {
      val value = expression.findAssignedValue() as? PyCallExpression ?: return null
      val callee = value.callee as? PyReferenceExpression ?: return null

      val calleeNameAndType = calculateCalleeNameAndType(callee) ?: return null
      val parameters = analyzeArguments(value, calleeNameAndType.second) ?: return null

      return PyDataclassFieldStubImpl(calleeNameAndType.first, parameters)
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyDataclassFieldStub? {
      val calleeName = stream.readNameString() ?: return null
      val hasDefault = stream.readBoolean()
      val hasDefaultFactory = stream.readBoolean()
      val initValue = stream.readBoolean()
      val kwOnly = stream.readBoolean()

      return PyDataclassFieldStubImpl(
        QualifiedName.fromDottedString(calleeName),
        FieldParameters(hasDefault, hasDefaultFactory, initValue, kwOnly)
      )
    }

    private fun calculateCalleeNameAndType(callee: PyReferenceExpression): Pair<QualifiedName, PyDataclassParameters.PredefinedType>? {
      val qualifiedName = callee.asQualifiedName() ?: return null

      val dataclassesField = QualifiedName.fromComponents("dataclasses", "field")
      val attrIb = QualifiedName.fromComponents("attr", "ib")
      val attrAttr = QualifiedName.fromComponents("attr", "attr")
      val attrAttrib = QualifiedName.fromComponents("attr", "attrib")

      for (originalQName in PyResolveUtil.resolveImportedElementQNameLocally(callee)) {
        when (originalQName) {
          dataclassesField -> return qualifiedName to PyDataclassParameters.PredefinedType.STD
          attrIb, attrAttr, attrAttrib -> return qualifiedName to PyDataclassParameters.PredefinedType.ATTRS
        }
      }

      return null
    }

    private fun analyzeArguments(call: PyCallExpression, type: PyDataclassParameters.PredefinedType): FieldParameters? {
      val initValue = PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("init"), true)

      if (type == PyDataclassParameters.PredefinedType.STD) {
        val default = call.getKeywordArgument("default")
        val defaultFactory = call.getKeywordArgument("default_factory")

        return FieldParameters(default != null && !resolvesToOmittedDefault(default, type),
                               defaultFactory != null && !resolvesToOmittedDefault(defaultFactory, type),
                               initValue,
                               false)
      }
      else if (type == PyDataclassParameters.PredefinedType.ATTRS) {
        val default = call.getKeywordArgument("default")
        val hasFactory = call.getKeywordArgument("factory").let { it != null && it.text != PyNames.NONE }
        val kwOnly = PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("kw_only"), false)

        if (default != null && !resolvesToOmittedDefault(default, type)) {
          val callee = (default as? PyCallExpression)?.callee as? PyReferenceExpression
          val hasFactoryInDefault =
            callee != null &&
            QualifiedName.fromComponents("attr", "Factory") in PyResolveUtil.resolveImportedElementQNameLocally(callee)

          return FieldParameters(!hasFactoryInDefault, hasFactory || hasFactoryInDefault, initValue, kwOnly)
        }

        return FieldParameters(false, hasFactory, initValue, kwOnly)
      }

      return null
    }
  }

  override fun getTypeClass(): Class<out CustomTargetExpressionStubType<out CustomTargetExpressionStub>> {
    return PyDataclassFieldStubType::class.java
  }

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(calleeName.toString())
    stream.writeBoolean(parameters.hasDefault)
    stream.writeBoolean(parameters.hasDefaultFactory)
    stream.writeBoolean(parameters.initValue)
    stream.writeBoolean(parameters.kwOnly)
  }

  override fun getCalleeName(): QualifiedName = calleeName
  override fun hasDefault(): Boolean = parameters.hasDefault
  override fun hasDefaultFactory(): Boolean = parameters.hasDefaultFactory
  override fun initValue(): Boolean = parameters.initValue
  override fun kwOnly(): Boolean = parameters.kwOnly

  private data class FieldParameters(val hasDefault: Boolean,
                                     val hasDefaultFactory: Boolean,
                                     val initValue: Boolean,
                                     val kwOnly: Boolean)
}
