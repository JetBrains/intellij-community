// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools

import com.intellij.codeInspection.LocalInspectionTool
import com.jetbrains.python.inspections.PyAbstractClassInspection
import com.jetbrains.python.inspections.PyArgumentListInspection
import com.jetbrains.python.inspections.PyAssertTypeInspection
import com.jetbrains.python.inspections.PyCallingNonCallableInspection
import com.jetbrains.python.inspections.PyClassVarInspection
import com.jetbrains.python.inspections.PyDataclassInspection
import com.jetbrains.python.inspections.PyDataclassTransformInspection
import com.jetbrains.python.inspections.PyDeprecationInspection
import com.jetbrains.python.inspections.PyDisjointBaseInspection
import com.jetbrains.python.inspections.PyDunderSlotsInspection
import com.jetbrains.python.inspections.PyEnumInspection
import com.jetbrains.python.inspections.PyFinalInspection
import com.jetbrains.python.inspections.PyInitNewSignatureInspection
import com.jetbrains.python.inspections.PyMethodOverridingInspection
import com.jetbrains.python.inspections.PyNamedTupleInspection
import com.jetbrains.python.inspections.PyNewStyleGenericSyntaxInspection
import com.jetbrains.python.inspections.PyNewTypeInspection
import com.jetbrains.python.inspections.PyOverloadsInspection
import com.jetbrains.python.inspections.PyOverridesInspection
import com.jetbrains.python.inspections.PyProtocolInspection
import com.jetbrains.python.inspections.PyStdlibDataclassInspection
import com.jetbrains.python.inspections.PyTupleItemAssignmentInspection
import com.jetbrains.python.inspections.PyTypeAliasRedeclarationInspection
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.PyTypeHintsInspection
import com.jetbrains.python.inspections.PyTypedDictInspection
import com.jetbrains.python.inspections.PyUnboundLocalVariableInspection
import com.jetbrains.python.inspections.PyUnhashableInspection
import com.jetbrains.python.inspections.PyVarianceInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection

/** The inspections that check the typing conformance suite. Each one reports a rule of the typing specification. */
object PyConformanceInspections {
  val classes: List<Class<out LocalInspectionTool>> = listOf(
    PyAbstractClassInspection::class.java,
    PyArgumentListInspection::class.java,
    PyAssertTypeInspection::class.java,
    PyCallingNonCallableInspection::class.java,
    PyClassVarInspection::class.java,
    PyDataclassInspection::class.java,
    PyStdlibDataclassInspection::class.java,
    PyDataclassTransformInspection::class.java,
    PyDeprecationInspection::class.java,
    PyDisjointBaseInspection::class.java,
    PyDunderSlotsInspection::class.java,
    PyEnumInspection::class.java,
    PyFinalInspection::class.java,
    PyInitNewSignatureInspection::class.java,
    PyMethodOverridingInspection::class.java,
    PyNamedTupleInspection::class.java,
    PyNewStyleGenericSyntaxInspection::class.java,
    PyNewTypeInspection::class.java,
    PyOverloadsInspection::class.java,
    PyOverridesInspection::class.java,
    PyProtocolInspection::class.java,
    PyTupleItemAssignmentInspection::class.java,
    PyTypedDictInspection::class.java,
    PyTypeCheckerInspection::class.java,
    PyTypeHintsInspection::class.java,
    PyTypeAliasRedeclarationInspection::class.java,
    PyUnboundLocalVariableInspection::class.java,
    PyUnhashableInspection::class.java,
    PyUnresolvedReferencesInspection::class.java,
    PyVarianceInspection::class.java,
  )
}
