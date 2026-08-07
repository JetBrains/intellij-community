package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.codeInsight.DataclassParameterArgumentMapping
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.PyDataclassParametersProvider
import com.jetbrains.python.codeInsight.resolvesToOmittedDefault
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.TypeEvalContext

internal class PyDataclassTransformParametersProvider : PyDataclassParametersProvider {
  override fun getType(): PyDataclassParameters.Type = PyDataclassTransformType

  override fun buildDataclassStub(cls: PyClass, context: TypeEvalContext?): Pair<PyDataclassStub, DataclassParameterArgumentMapping>? =
    buildDataclassTransformStubAndMapping(cls, context)

  override fun buildDataclassFieldStub(expression: PyTargetExpression): PyDataclassFieldStub? {
    val call = PyDataclassFieldStubUtil.fieldInitializerCall(expression) ?: return null
    val fieldSpecifierKeywords = PyDataclassNames.DataclassTransform.FIELD_SPECIFIER_PARAMETERS
    if (fieldSpecifierKeywords.none { call.getKeywordArgument(it) != null }) {
      return null
    }

    val args = PyDataclassFieldStubUtil.parseCommonFieldCallArgs(call, usePositionalDefault = true) ?: return null
    val resolver = PyDataclassTransformType.resolver
    return PyDataclassFieldStubImpl(
      type = PyDataclassTransformType.name,
      calleeName = args.qualifiedName,
      hasDefault = args.default != null && !resolver.resolvesToOmittedDefault(args.default),
      hasDefaultFactory = args.defaultFactory != null && !resolver.resolvesToOmittedDefault(args.defaultFactory) ||
                          args.factory != null && !resolver.resolvesToOmittedDefault(args.factory),
      initValue = args.initValue,  // TODO How should we handle custom field specifiers where init=False by default
      kwOnly = args.kwOnly,
      alias = args.alias,
    )
  }
}

object PyDataclassTransformType : PyDataclassParameters.Type {
  override val name: String = "DATACLASS_TRANSFORM"
  override val isDataclassTransformBased: Boolean = true
  override val resolver: PyDataclassResolver = PyDataclassTransformResolver
}
