/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.*
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

      val calleeName = calculateFullyQCalleeName(callee) ?: calculateImportedCalleeName(callee) ?: return null
      val arguments = analyzeArguments(value)

      return PyDataclassFieldStubImpl(calleeName, arguments.first, arguments.second, arguments.third)
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyDataclassFieldStub? {
      val calleeName = stream.readName() ?: return null
      val hasDefault = stream.readBoolean()
      val hasDefaultFactory = stream.readBoolean()
      val initValue = stream.readBoolean()

      return PyDataclassFieldStubImpl(QualifiedName.fromDottedString(calleeName.string), hasDefault, hasDefaultFactory, initValue)
    }

    private fun calculateFullyQCalleeName(callee: PyReferenceExpression): QualifiedName? {
      // SUPPORTED CASES:

      // import dataclasses
      // ... = dataclasses.field(...)

      // import dataclasses as dc
      // ... = dc.field(...)

      val calleeName = callee.name
      val qualifier = callee.qualifier

      if (calleeName == "field" && qualifier is PyReferenceExpression && !qualifier.isQualified && resolvesToDataclassesModule(qualifier)) {
        return QualifiedName.fromComponents(qualifier.name, calleeName)
      }

      return null
    }

    private fun calculateImportedCalleeName(callee: PyReferenceExpression): QualifiedName? {
      // SUPPORTED CASES:

      // from dataclasses import field
      // ... = field(...)

      // from dataclasses import field as F
      // ... = F(...)

      for (element in PyResolveUtil.resolveLocally(callee)) {
        if (element is PyImportElement && element.importedQName.toString() == "field") {
          val importStatement = element.containingImportStatement
          if (importStatement is PyFromImportStatement && importStatement.importSourceQName.toString() == "dataclasses") {
            return QualifiedName.fromComponents(callee.name)
          }
        }
      }

      return null
    }

    private fun analyzeArguments(call: PyCallExpression): Triple<Boolean, Boolean, Boolean> {
      val hasDefault = call.getKeywordArgument("default") != null
      val hasDefaultFactory = call.getKeywordArgument("default_factory") != null
      val initValue = PyEvaluator().evaluate(call.getKeywordArgument("init")) as? Boolean ?: true

      return Triple(hasDefault, hasDefaultFactory, initValue)
    }

    private fun resolvesToDataclassesModule(referenceExpression: PyReferenceExpression): Boolean {
      return PyResolveUtil.resolveLocally(referenceExpression).any { it is PyImportElement && it.importedQName.toString() == "dataclasses" }
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

  override fun getCalleeName() = calleeName
  override fun hasDefault() = hasDefault
  override fun hasDefaultFactory() = hasDefaultFactory
  override fun initValue() = initValue
}