// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unusedLocal

import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.pane
import com.jetbrains.python.PyPsiBundle

/**
 * Reports unused function parameters. Unused local variables and other local symbols are reported separately by
 * [PyUnusedLocalVariableInspection]; both inspections share [PyUnusedLocalInspectionVisitor] for the analysis.
 */
class PyUnusedParameterInspection : PyUnusedSymbolInspection() {
  @JvmField
  var ignoreLambdaParameters: Boolean = true

  override val reportTarget: PyUnusedLocalInspectionVisitor.ReportTarget
    get() = PyUnusedLocalInspectionVisitor.ReportTarget.PARAMETERS

  override fun shouldIgnoreLambdaParameters(): Boolean = ignoreLambdaParameters

  override fun getOptionsPane(): OptPane = pane(
    OptPane.checkbox("ignoreLambdaParameters", PyPsiBundle.message("INSP.unused.locals.ignore.lambda.parameters")),
  )
}
