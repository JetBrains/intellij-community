package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.codeInsight.DataclassParameterArgumentMapping
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.PyDataclassParametersProvider
import com.jetbrains.python.codeInsight.buildDecoratorDataclassStubAndMapping
import com.jetbrains.python.codeInsight.resolvesToOmittedDefault
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Dataclasses
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.TypeEvalContext

internal class PyStdlibDataclassParametersProvider : PyDataclassParametersProvider {
  override fun getType(): PyDataclassParameters.Type = PyStdlibDataclassType

  override fun buildDataclassStub(cls: PyClass, context: TypeEvalContext?): Pair<PyDataclassStub, DataclassParameterArgumentMapping>? =
    buildDecoratorDataclassStubAndMapping(cls, context)

  override fun buildDataclassFieldStub(expression: PyTargetExpression): PyDataclassFieldStub? {
    val call = PyDataclassFieldStubUtil.fieldInitializerCall(expression) ?: return null
    if (PyDataclassFieldStubUtil.calleeQualifiedNames(call).none { it == Dataclasses.DATACLASSES_FIELD }) return null

    val args = PyDataclassFieldStubUtil.parseCommonFieldCallArgs(call, usePositionalDefault = false) ?: return null
    val resolver = PyStdlibDataclassType.resolver
    return PyDataclassFieldStubImpl(
      type = PyStdlibDataclassType.name,
      calleeName = args.qualifiedName,
      hasDefault = args.default != null && !resolver.resolvesToOmittedDefault(args.default),
      hasDefaultFactory = args.defaultFactory != null && !resolver.resolvesToOmittedDefault(args.defaultFactory) ||
                          args.factory != null && !resolver.resolvesToOmittedDefault(args.factory),
      initValue = args.initValue,
      kwOnly = args.kwOnly,
      alias = null,
    )
  }
}

object PyStdlibDataclassType : PyDataclassParameters.Type {
  override val name: String = "STD"
  override val isDataclassTransformBased: Boolean = false
  override val resolver: PyDataclassResolver = PyStdlibDataclassResolver
}
