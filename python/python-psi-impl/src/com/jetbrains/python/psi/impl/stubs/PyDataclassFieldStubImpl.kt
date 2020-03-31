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
                                                   private val hasDefault: Boolean,
                                                   private val hasDefaultFactory: Boolean,
                                                   private val initValue: Boolean) : PyDataclassFieldStub {
  companion object {
    fun create(expression: PyTargetExpression): PyDataclassFieldStub? {
      val value = expression.findAssignedValue() as? PyCallExpression ?: return null
      val callee = value.callee as? PyReferenceExpression ?: return null

      val calleeNameAndType = calculateCalleeNameAndType(callee) ?: return null
      val arguments = analyzeArguments(value, calleeNameAndType.second) ?: return null

      return PyDataclassFieldStubImpl(calleeNameAndType.first, arguments.first, arguments.second, arguments.third)
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyDataclassFieldStub? {
      val calleeName = stream.readNameString() ?: return null
      val hasDefault = stream.readBoolean()
      val hasDefaultFactory = stream.readBoolean()
      val initValue = stream.readBoolean()

      return PyDataclassFieldStubImpl(QualifiedName.fromDottedString(calleeName), hasDefault, hasDefaultFactory, initValue)
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

    private fun analyzeArguments(call: PyCallExpression, type: PyDataclassParameters.PredefinedType): Triple<Boolean, Boolean, Boolean>? {
      val initValue = PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("init"), true)

      if (type == PyDataclassParameters.PredefinedType.STD) {
        val default = call.getKeywordArgument("default")
        val defaultFactory = call.getKeywordArgument("default_factory")

        return Triple(default != null && !resolvesToOmittedDefault(default, type),
                      defaultFactory != null && !resolvesToOmittedDefault(defaultFactory, type),
                      initValue)
      }
      else if (type == PyDataclassParameters.PredefinedType.ATTRS) {
        val default = call.getKeywordArgument("default")
        val hasFactory = call.getKeywordArgument("factory").let { it != null && it.text != PyNames.NONE }

        if (default != null && !resolvesToOmittedDefault(default, type)) {
          val callee = (default as? PyCallExpression)?.callee as? PyReferenceExpression
          val hasFactoryInDefault =
            callee != null &&
            QualifiedName.fromComponents("attr", "Factory") in PyResolveUtil.resolveImportedElementQNameLocally(callee)

          return Triple(!hasFactoryInDefault, hasFactory || hasFactoryInDefault, initValue)
        }

        return Triple(false, hasFactory, initValue)
      }

      return null
    }
  }

  override fun getTypeClass(): Class<out CustomTargetExpressionStubType<out CustomTargetExpressionStub>> {
    return PyDataclassFieldStubType::class.java
  }

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(calleeName.toString())
    stream.writeBoolean(hasDefault)
    stream.writeBoolean(hasDefaultFactory)
    stream.writeBoolean(initValue)
  }

  override fun getCalleeName(): QualifiedName = calleeName
  override fun hasDefault(): Boolean = hasDefault
  override fun hasDefaultFactory(): Boolean = hasDefaultFactory
  override fun initValue(): Boolean = initValue
}
