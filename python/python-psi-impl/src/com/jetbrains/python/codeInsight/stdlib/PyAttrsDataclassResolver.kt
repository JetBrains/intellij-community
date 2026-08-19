// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.DataclassParameterArgumentMapping
import com.jetbrains.python.codeInsight.InitFieldsAccumulator
import com.jetbrains.python.codeInsight.PyDataclassCopyFunction
import com.jetbrains.python.codeInsight.PyDataclassField
import com.jetbrains.python.codeInsight.PyDataclassFieldParameters
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyNoneLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeUtil.widenLiteralAndNumeric
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isAnyOrUnknown

@Suppress("NullableBooleanElvis")
internal object PyAttrsDataclassResolver : PyDataclassResolver {
  override val omittedDefaultQualifiedNames: Set<String> = PyDataclassNames.Attrs.ATTRS_NOTHING

  override fun resolveClassParameters(
      pyClass: PyClass,
      stub: PyDataclassStub,
      type: PyDataclassParameters.Type,
      argumentMapping: DataclassParameterArgumentMapping?,
      context: TypeEvalContext,
  ): PyDataclassParameters? {
    if (stub.type != PyAttrsDataclassType.name) return null

    // TODO remove this hack, make it a proper field
    val extraArguments = mutableMapOf<String, PyExpression>()
    if (stub.decoratorName() == PyKnownDecorator.ATTR_DATACLASS.qualifiedName) {
      extraArguments["auto_attribs"] =
        PyElementGenerator.getInstance(pyClass.project).createExpressionFromText(LanguageLevel.forElement(pyClass), PyNames.TRUE)
    }

    return PyDataclassParameters(
        init = stub.initValue() ?: true,
        repr = stub.reprValue() ?: true,
        eq = stub.eqValue() ?: true,
        order = stub.orderValue() ?: true,
        unsafeHash = stub.unsafeHashValue() ?: false,
        frozen = stub.frozenValue() ?: (stub.decoratorName()?.toString() in PyDataclassNames.Attrs.ATTRS_FROZEN),
        matchArgs = stub.matchArgsValue() ?: true,
        kwOnly = stub.kwOnly() ?: false,
        slots = stub.slotsValue() ?: false,
        initArgument = argumentMapping?.initArgument,
        reprArgument = argumentMapping?.reprArgument,
        eqArgument = argumentMapping?.eqArgument,
        orderArgument = argumentMapping?.orderArgument,
        unsafeHashArgument = argumentMapping?.unsafeHashArgument,
        frozenArgument = argumentMapping?.frozenArgument,
        matchArgsArgument = argumentMapping?.matchArgsArgument,
        kwOnlyArgument = argumentMapping?.kwOnlyArgument,
        slotsArgument = argumentMapping?.slotsArgument,
        others = (argumentMapping?.others ?: emptyMap()) + extraArguments,
        type = type,
        fieldSpecifiers = PyDataclassNames.Attrs.FIELD_FUNCTIONS.map(QualifiedName::fromDottedString),
    )
  }

  override fun getInitParameterName(fieldName: String, fieldParams: PyDataclassFieldParameters?): String =
    // Fields starting with more than one underscore will be mangled into ClassName__field_name, but we don't support that
    fieldParams?.parameterAlias ?: fieldName.removePrefix("_")

  override fun getInitParameterType(cls: PyClass, field: PyTargetExpression, context: TypeEvalContext): PyType? {
    if (context.maySwitchToAST(field)) {
      (field.findAssignedValue() as? PyCallExpression)
        ?.getKeywordArgument("type")
        ?.let { PyTypingTypeProvider.getType(it, context) }
        ?.apply { return get() }
    }

    val type = super.getInitParameterType(cls, field, context)
    if (type.isAnyOrUnknown) {
      methodDecoratedAsAttributeDefault(cls, field.name)?.getReturnType(context)
        ?.let { return PyUnionType.createWeakType(widenLiteralAndNumeric(it)) }
    }
    return type
  }

  override fun getInitParameterDefault(
      cls: PyClass,
      field: PyTargetExpression,
      fieldParams: PyDataclassFieldParameters?,
      context: TypeEvalContext,
  ): PyExpression? {
    if (fieldParams == null) {
      return super.getInitParameterDefault(cls, field, fieldParams, context)
    }
    return if (fieldParams.hasDefault ||
               fieldParams.hasDefaultFactory ||
               methodDecoratedAsAttributeDefault(cls, field.name) != null) {
      val elementGenerator = PyElementGenerator.getInstance(cls.project)
      elementGenerator.createEllipsis()
    }
    else {
      null
    }
  }

  override fun placeFieldInInitSignature(
      acc: InitFieldsAccumulator,
      fieldInfo: PyDataclassField,
      index: Int,
      indexOfKeywordOnlyAttribute: Int,
      context: TypeEvalContext,
  ) {
    val aliasOrFieldName = fieldInfo.parameterName
    val kwOnly = fieldInfo.kwOnly
    val parameter = fieldInfo.parameter
    val fieldName = fieldInfo.name

    // attrs: a kw_only class makes every field keyword-only (not only those with kw_only != false).
    if ((acc.seenKeywordOnlyClass || index < indexOfKeywordOnlyAttribute || kwOnly == true)
        && aliasOrFieldName !in acc.positionalAliasOrFieldNameParams) {
      acc.keywordOnlyAliasOrFieldNameParams += aliasOrFieldName
      acc.keywordOnlyFieldNameParams += fieldName
    }

    if (parameter == null) {
      acc.seenNames.add(aliasOrFieldName)
    }
    else if (!isKwOnlyMarkerField(parameter, context) && aliasOrFieldName !in acc.positionalAliasOrFieldNameParams) {
      // attrs: an attribute that overrides an ancestor's attribute changes the order
      acc.positionalAliasOrFieldNameParams[aliasOrFieldName] = parameter
    }
  }

  private fun methodDecoratedAsAttributeDefault(cls: PyClass, attributeName: String?): PyFunction? {
    if (attributeName == null) return null
    return cls.methods.firstOrNull { it.decoratorList?.findDecorator("$attributeName.default") != null }
  }

  override fun copyFunctions(): List<PyDataclassCopyFunction> =
    (PyDataclassNames.Attrs.ATTRS_ASSOC + PyDataclassNames.Attrs.ATTRS_EVOLVE).map {
        PyDataclassCopyFunction(QualifiedName.fromDottedString(it), "inst")
    }

  override fun applyCustomDecoratorParameter(
    accumulator: ParametersAccumulator,
    name: String,
    value: PyExpression?,
    argument: PyExpression?,
  ): Boolean {
    when (name) {
      "eq" -> {
        accumulator.eq = PyEvaluator.evaluateAsBooleanNoResolve(value)
        accumulator.eqArgument = argument
        if (accumulator.orderArgument == null && accumulator.eqArgument != null) {
          accumulator.order = accumulator.eq
          accumulator.orderArgument = accumulator.eqArgument
        }
      }
      "order" -> {
        if (argument !is PyNoneLiteralExpression) {
          accumulator.order = PyEvaluator.evaluateAsBooleanNoResolve(value)
          accumulator.orderArgument = argument
        }
      }
      "cmp" -> {
        accumulator.eq = PyEvaluator.evaluateAsBooleanNoResolve(value)
        accumulator.eqArgument = argument
        accumulator.order = accumulator.eq
        accumulator.orderArgument = accumulator.eqArgument
      }
      "hash" -> {
        accumulator.unsafeHash = PyEvaluator.evaluateAsBooleanNoResolve(value)
        accumulator.unsafeHashArgument = argument
      }
      else -> return false
    }
    return true
  }
}
