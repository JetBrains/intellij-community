// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.DataclassParameterArgumentMapping
import com.jetbrains.python.codeInsight.PyDataclassCopyFunction
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Dataclasses
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.types.TypeEvalContext

@Suppress("NullableBooleanElvis")
internal object PyStdlibDataclassResolver : PyDataclassResolver {
  override val omittedDefaultQualifiedNames: Set<String> = Dataclasses.OMITTED_DEFAULTS

  override fun resolveClassParameters(
    pyClass: PyClass,
    stub: PyDataclassStub,
    type: PyDataclassParameters.Type,
    argumentMapping: DataclassParameterArgumentMapping?,
    context: TypeEvalContext,
  ): PyDataclassParameters? {
    if (stub.type != PyStdlibDataclassType.name) return null
    return PyDataclassParameters(
      init = stub.initValue() ?: true,
      repr = stub.reprValue() ?: true,
      eq = stub.eqValue() ?: true,
      order = stub.orderValue() ?: false,
      unsafeHash = stub.unsafeHashValue() ?: false,
      frozen = stub.frozenValue() ?: false,
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
      others = argumentMapping?.others ?: emptyMap(),
      type = type,
      fieldSpecifiers = listOf(QualifiedName.fromDottedString(Dataclasses.DATACLASSES_FIELD)),
    )
  }

  override fun copyFunctions(): List<PyDataclassCopyFunction> = listOf(
    PyDataclassCopyFunction(QualifiedName.fromDottedString(Dataclasses.DATACLASSES_REPLACE), "obj"),
  )

  /** Name of the special post-init hook Python calls after `__init__`. */
  override fun postInitFunctionName(): String = Dataclasses.DUNDER_POST_INIT
}
