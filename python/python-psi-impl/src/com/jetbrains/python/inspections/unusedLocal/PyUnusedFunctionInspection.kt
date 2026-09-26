// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unusedLocal

/**
 * Reports unused local functions. Unused local variables and other local symbols are reported separately by
 * [PyUnusedLocalVariableInspection]; both inspections share [PyUnusedLocalInspectionVisitor] for the analysis.
 */
class PyUnusedFunctionInspection : PyUnusedSymbolInspection() {
  override val reportTarget: PyUnusedLocalInspectionVisitor.ReportTarget
    get() = PyUnusedLocalInspectionVisitor.ReportTarget.FUNCTIONS
}
