// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiReference
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.jetbrains.python.console.PyConsoleIndentUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression

internal class PyDebuggerEvaluator(private val myProject: Project, private val myDebugProcess: PyFrameAccessor) : XDebuggerEvaluator(), XDebuggerDocumentOffsetEvaluator {
  override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?): Unit =
    doEvaluate(expression, callback, true)


  private val none: PyDebugValue
    get() = PyDebugValue("", "NoneType", null, "None", false, null, null, false, false, false, null, null, myDebugProcess)

  private fun doEvaluate(expr: String, callback: XEvaluationCallback, doTrunc: Boolean) {
    ApplicationManager.getApplication().executeOnPooledThread(Runnable {
      val expression = expr.trim { it <= ' ' }
      if (expression.isEmpty()) {
        callback.evaluated(this.none)
        return@Runnable
      }

      val isExpression = PyDebugSupportUtils.isExpression(myProject, expression)
      try {
        // todo: think on getting results from EXEC
        val value = myDebugProcess.evaluate(expression, !isExpression, doTrunc)
        if (value.isErrorOnEval) {
          callback.errorOccurred("{" + value.type + "}" + value.value) //NON-NLS
        }
        else {
          callback.evaluated(value)
        }
      }
      catch (e: PyDebuggerException) {
        callback.errorOccurred(e.getTracebackError())
      }
    })
  }

  override fun getExpressionRangeAtOffset(project: Project?, document: Document?,  offset: Int, sideEffectsAllowed: Boolean): TextRange? =
    PyDebugSupportUtils.getExpressionRangeAtOffset(project, document, offset)

  override fun formatTextForEvaluation(text: String): String = PyConsoleIndentUtil.normalize(text)

  override fun evaluate(document: Document, offset: Int, hintType: ValueHintType, callback: XEvaluationCallback) {
    if (!Registry.`is`("python.debugger.show.value.tooltip")) {
      callback.errorOccurred("")
      return
    }

    val ref = ReadAction.compute<PsiReference?, RuntimeException?>(ThrowableComputable {
      PsiDocumentManager.getInstance(myProject).getPsiFile(document)?.findReferenceAt(offset)
    })
    if (ref == null) {
      callback.errorOccurred("")
      return
    }

    val element = ref.getElement()
    when(element) {
      is PyExpression -> {
        if (element.getParent() is PyCallExpression) {
          callback.errorOccurred("")
        } else {
          doEvaluate(element.getText(), callback, false)
        }
      }
      else -> callback.errorOccurred("")
    }
  }
}