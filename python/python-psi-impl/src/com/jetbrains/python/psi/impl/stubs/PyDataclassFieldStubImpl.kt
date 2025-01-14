/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.impl.stubs

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.util.QualifiedName
import com.intellij.util.io.DataInputOutputUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyDataclassNames
import com.jetbrains.python.codeInsight.PyDataclassNames.Attrs
import com.jetbrains.python.codeInsight.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.resolvesToOmittedDefault
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import java.io.IOException

class PyDataclassFieldStubImpl private constructor(private val calleeName: QualifiedName,
                                                   private val hasDefault: Boolean,
                                                   private val hasDefaultFactory: Boolean,
                                                   private val initValue: Boolean,
                                                   private val kwOnly: Boolean?,
                                                   private val alias: String?,
) : PyDataclassFieldStub {
  companion object {
    fun create(expression: PyTargetExpression): PyDataclassFieldStub? {
      val callExpr = expression.findAssignedValue() as? PyCallExpression ?: return null
      val predefinedType = calculateCalleeNameAndType(callExpr) ?: return null
      return analyzeArguments(callExpr, predefinedType)
    }

    @Throws(IOException::class)
    fun deserialize(stream: StubInputStream): PyDataclassFieldStub? {
      val calleeName = stream.readNameString() ?: return null
      val hasDefault = stream.readBoolean()
      val hasDefaultFactory = stream.readBoolean()
      val initValue = stream.readBoolean()
      val kwOnly = DataInputOutputUtil.readNullable(stream, stream::readBoolean)
      val alias = stream.readNameString()

      return PyDataclassFieldStubImpl(
        calleeName = QualifiedName.fromDottedString(calleeName),
        hasDefault = hasDefault,
        hasDefaultFactory = hasDefaultFactory,
        initValue = initValue,
        kwOnly = kwOnly,
        alias = alias,
      )
    }

    private fun calculateCalleeNameAndType(fieldInitializer: PyCallExpression): PyDataclassParameters.PredefinedType? {
      val callee = fieldInitializer.callee as? PyReferenceExpression ?: return null

      for (originalQName in PyResolveUtil.resolveImportedElementQNameLocally(callee)) {
        when (originalQName.toString()) {
          Dataclasses.DATACLASSES_FIELD -> return PyDataclassParameters.PredefinedType.STD
          in Attrs.FIELD_FUNCTIONS -> return PyDataclassParameters.PredefinedType.ATTRS
        }
      }

      // Any function call with these keyword arguments is a potential dataclass field initializer
      if (PyDataclassNames.DataclassTransform.FIELD_SPECIFIER_PARAMETERS.any { fieldInitializer.getKeywordArgument(it) != null }) {
        return PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM
      }

      return null
    }

    private fun analyzeArguments(call: PyCallExpression, type: PyDataclassParameters.PredefinedType): PyDataclassFieldStub? {
      val qualifiedName = (call.callee as? PyReferenceExpression)?.asQualifiedName() ?: return null
      val initValue = PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("init"), true)
      val kwOnly = PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("kw_only"))
      val default = call.getKeywordArgument("default")
      val defaultFactory = call.getKeywordArgument("default_factory")
      val factory = call.getKeywordArgument("factory")
      val alias = (call.getKeywordArgument("alias") as? PyStringLiteralExpression)?.stringValue

      if (type == PyDataclassParameters.PredefinedType.STD) {
        return PyDataclassFieldStubImpl(
          calleeName = qualifiedName,
          hasDefault = default != null && !resolvesToOmittedDefault(default, type),
          hasDefaultFactory = defaultFactory != null && !resolvesToOmittedDefault(defaultFactory, type),
          initValue = initValue,
          kwOnly = kwOnly,
          alias = null
        )
      }
      else if (type == PyDataclassParameters.PredefinedType.ATTRS) {
        val hasFactory = factory.let { it != null && it.text != PyNames.NONE }
        
        if (default != null && !resolvesToOmittedDefault(default, type)) {
          val callee = (default as? PyCallExpression)?.callee as? PyReferenceExpression
          val hasFactoryInDefault =
            callee != null &&
            PyResolveUtil.resolveImportedElementQNameLocally(callee).any { it.toString() in Attrs.ATTRS_FACTORY }

          return PyDataclassFieldStubImpl(
            calleeName = qualifiedName,
            hasDefault = !hasFactoryInDefault,
            hasDefaultFactory = hasFactory || hasFactoryInDefault,
            initValue = initValue,
            kwOnly = kwOnly,
            alias = alias
          )
        }

        return PyDataclassFieldStubImpl(
          calleeName = qualifiedName,
          hasDefault = false,
          hasDefaultFactory = hasFactory,
          initValue = initValue,
          kwOnly = kwOnly,
          alias = alias
        )
      }
      else if (type == PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM) {
        return PyDataclassFieldStubImpl(
          calleeName = qualifiedName,
          // dataclasses.MISSING is not mentioned in the spec, but because dataclasses.KW_ONLY is supported, 
          // this one is special-cases as well
          hasDefault = default != null && !resolvesToOmittedDefault(default, type),
          hasDefaultFactory = defaultFactory != null && !resolvesToOmittedDefault(defaultFactory, type) || 
                              factory != null && !resolvesToOmittedDefault(factory, type),
          initValue = initValue,  // TODO How should we handle custom field specifiers where init=False by default
          kwOnly = kwOnly,
          alias = alias
        )
      }

      return null
    }
  }

  override fun getTypeClass(): Class<PyDataclassFieldStubType> {
    return PyDataclassFieldStubType::class.java
  }

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(calleeName.toString())
    stream.writeBoolean(hasDefault)
    stream.writeBoolean(hasDefaultFactory)
    stream.writeBoolean(initValue)
    DataInputOutputUtil.writeNullable(stream, kwOnly, stream::writeBoolean)
    stream.writeName(alias)
  }

  override fun getCalleeName(): QualifiedName = calleeName
  override fun hasDefault(): Boolean = hasDefault
  override fun hasDefaultFactory(): Boolean = hasDefaultFactory
  override fun initValue(): Boolean = initValue
  override fun kwOnly(): Boolean? = kwOnly
  override fun getAlias(): String? = alias

  override fun toString(): String {
    return "PyDataclassFieldStubImpl(" +
           "calleeName=$calleeName, " +
           "hasDefault=$hasDefault, " +
           "hasDefaultFactory=$hasDefaultFactory, " +
           "initValue=$initValue, " +
           "kwOnly=$kwOnly, " +
           "alias=$alias" +
           ")"
  }
}
