package com.jetbrains.python.codeInsight

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.stubs.PyDataclassStubImpl
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.TypeEvalContext

@JvmDefaultWithCompatibility
interface PyDataclassParametersProvider {

  companion object {
    val EP_NAME: ExtensionPointName<PyDataclassParametersProvider> = ExtensionPointName.Companion.create("Pythonid.pyDataclassParametersProvider")
  }

  fun getType(): PyDataclassParameters.Type

  fun getDecoratorAndTypeAndParameters(project: Project): Triple<QualifiedName, PyDataclassParameters.Type, List<PyCallableParameter>>? = null

  @Deprecated("Use buildDataclassStub(PyClass, TypeEvalContext?) instead.")
  fun getDataclassParameters(cls: PyClass, context: TypeEvalContext?): PyDataclassParameters? = null

  fun buildDataclassStub(cls: PyClass, context: TypeEvalContext?): Pair<PyDataclassStub, DataclassParameterArgumentMapping>? {
    @Suppress("DEPRECATION")
    val provided = getDataclassParameters(cls, context) ?: return null
    return PyDataclassStubImpl.of(
      type = provided.type,
      decoratorName = null,
      init = provided.init,
      repr = provided.repr,
      eq = provided.eq,
      order = provided.order,
      unsafeHash = provided.unsafeHash,
      frozen = provided.frozen,
      matchArgs = provided.matchArgs,
      kwOnly = provided.kwOnly,
      slots = provided.slots,
    ) to DataclassParameterArgumentMapping(
      initArgument = provided.initArgument,
      reprArgument = provided.reprArgument,
      eqArgument = provided.eqArgument,
      orderArgument = provided.orderArgument,
      unsafeHashArgument = provided.unsafeHashArgument,
      frozenArgument = provided.frozenArgument,
      matchArgsArgument = provided.matchArgsArgument,
      kwOnlyArgument = provided.kwOnlyArgument,
      slotsArgument = provided.slotsArgument,
      others = provided.others,
    )
  }

  fun buildDataclassFieldStub(expression: PyTargetExpression): PyDataclassFieldStub? = null
}