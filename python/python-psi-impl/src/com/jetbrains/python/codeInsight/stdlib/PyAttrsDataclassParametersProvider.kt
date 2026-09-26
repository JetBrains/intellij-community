package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.DataclassParameterArgumentMapping
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.PyDataclassParametersProvider
import com.jetbrains.python.codeInsight.buildDecoratorDataclassStubAndMapping
import com.jetbrains.python.codeInsight.resolvesToOmittedDefault
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Attrs
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.TypeEvalContext

internal class PyAttrsDataclassParametersProvider : PyDataclassParametersProvider {
  override fun getType(): PyDataclassParameters.Type = PyAttrsDataclassType

  override fun buildDataclassStub(cls: PyClass, context: TypeEvalContext?): Pair<PyDataclassStub, DataclassParameterArgumentMapping>? =
    buildDecoratorDataclassStubAndMapping(cls, context)

  override fun buildDataclassFieldStub(expression: PyTargetExpression): PyDataclassFieldStub? {
    val call = PyDataclassFieldStubUtil.fieldInitializerCall(expression) ?: return null
    if (PyDataclassFieldStubUtil.calleeQualifiedNames(call).none { it in Attrs.FIELD_FUNCTIONS }) return null

    val args = PyDataclassFieldStubUtil.parseCommonFieldCallArgs(call, usePositionalDefault = false) ?: return null
    val resolver = PyAttrsDataclassType.resolver
    val hasFactory = args.factory.let { it != null && it.text != PyNames.NONE }

    if (args.default != null && !resolver.resolvesToOmittedDefault(args.default)) {
      val callee = (args.default as? PyCallExpression)?.callee as? PyReferenceExpression
      val hasFactoryInDefault =
        callee != null &&
        PyResolveUtil.resolveImportedElementQNameLocally(callee).any { it.toString() in Attrs.ATTRS_FACTORY }

      return PyDataclassFieldStubImpl(
        type = PyAttrsDataclassType.name,
        calleeName = args.qualifiedName,
        hasDefault = !hasFactoryInDefault,
        hasDefaultFactory = hasFactory || hasFactoryInDefault,
        initValue = args.initValue,
        kwOnly = args.kwOnly,
        alias = args.alias,
      )
    }
    return PyDataclassFieldStubImpl(
      type = PyAttrsDataclassType.name,
      calleeName = args.qualifiedName,
      hasDefault = false,
      hasDefaultFactory = hasFactory,
      initValue = args.initValue,
      kwOnly = args.kwOnly,
      alias = args.alias,
    )
  }
}

object PyAttrsDataclassType : PyDataclassParameters.Type {
  override val name: String = "ATTRS"
  override val isDataclassTransformBased: Boolean = false
  override val resolver: PyDataclassResolver = PyAttrsDataclassResolver
}
