// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unusedLocal

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor

/**
 * Base class for the unused-parameter and unused-function inspections. Both report a single kind of unused symbol via the shared
 * [PyUnusedLocalInspectionVisitor]; the actual control-flow analysis is shared across all unused-symbol inspections running on the
 * same session (see [PyUnusedLocalInspectionVisitor.SharedAnalysis]). Unused local variables and other local symbols are reported
 * separately by [PyUnusedLocalVariableInspection].
 */
abstract class PyUnusedSymbolInspection : PyInspection() {
  private val visitorKey = Key.create<PyUnusedLocalInspectionVisitor>("${javaClass.simpleName}.Visitor")

  /** The single element kind this inspection reports. */
  protected abstract val reportTarget: PyUnusedLocalInspectionVisitor.ReportTarget

  /** Whether lambda parameters should be ignored; only meaningful when [reportTarget] is `PARAMETERS`. */
  protected open fun shouldIgnoreLambdaParameters(): Boolean = true

  final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val visitor = PyUnusedLocalInspectionVisitor(holder, false, shouldIgnoreLambdaParameters(), false, false,
                                                 reportTarget,
                                                 PyUnusedLocalInspectionVisitor.getSharedAnalysis(session),
                                                 PyInspectionVisitor.getContext(session))
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    if (session.getUserData(visitorKey) == null) {
      session.putUserData(visitorKey, visitor)
    }
    return visitor
  }

  final override fun inspectionFinished(session: LocalInspectionToolSession, holder: ProblemsHolder) {
    val visitor = session.getUserData(visitorKey) ?: return
    runReadActionBlocking { visitor.registerProblems() }
    session.putUserData(visitorKey, null)
  }
}
