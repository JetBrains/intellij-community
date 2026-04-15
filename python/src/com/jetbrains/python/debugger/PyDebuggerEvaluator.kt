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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.jetbrains.python.console.PyConsoleIndentUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyLiteralExpression
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import org.jetbrains.annotations.ApiStatus

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

  override fun getExpressionRangeAtOffset(project: Project?, document: Document?, offset: Int, sideEffectsAllowed: Boolean): TextRange? {
    val range = PyDebugSupportUtils.getExpressionRangeAtOffset(project, document, offset) ?: return null
    if (sideEffectsAllowed || project == null || document == null) return range
    return filterExpressionRangeForHover(project, document, offset, range)
  }

  override fun formatTextForEvaluation(text: String): String = PyConsoleIndentUtil.normalize(text)

  override fun evaluate(document: Document, offset: Int, hintType: ValueHintType, callback: XEvaluationCallback) {
    evaluateWithOffset(myProject, document, offset, hintType, callback) { expression ->
      doEvaluate(expression, callback, false)
    }
  }
}

/**
 * Common implementation for evaluating expressions at a given document offset (for value tooltips).
 *
 * @param project the project
 * @param document the document
 * @param offset the offset in the document
 * @param hintType the hint type
 * @param callback the evaluation callback
 * @param evaluateExpression the function to evaluate the expression text
 */
@ApiStatus.Internal
fun evaluateWithOffset(
  project: Project,
  document: Document,
  offset: Int,
  hintType: ValueHintType,
  callback: XEvaluationCallback,
  evaluateExpression: (String) -> Unit,
) {
  if (!Registry.`is`("python.debugger.show.value.tooltip")) {
    callback.errorOccurred("")
    return
  }

  val ref = ReadAction.compute<PsiReference?, RuntimeException?>(ThrowableComputable {
    PsiDocumentManager.getInstance(project).getPsiFile(document)?.findReferenceAt(offset)
  })
  if (ref == null) {
    callback.errorOccurred("")
    return
  }

  val element = ref.element
  when (element) {
    is PyExpression -> {
      if (element.parent is PyCallExpression) {
        callback.errorOccurred("")
      }
      else {
        evaluateExpression(element.text)
      }
    }
    else -> callback.errorOccurred("")
  }
}

/**
 * Filters the expression range for hover evaluation, excluding expressions that may have side effects (PY-83446).
 *
 * On hover, we avoid evaluating call expressions, binary expressions, and prefix expressions
 * to prevent unintended function execution. For function names inside calls, only the reference
 * range is returned (not the full call).
 *
 * @param project the project
 * @param document the document
 * @param offset the offset in the document
 * @param range the original expression range from [PyDebugSupportUtils.getExpressionRangeAtOffset]
 * @return the filtered range, or null if the expression should not be evaluated on hover
 */
@ApiStatus.Internal
fun filterExpressionRangeForHover(project: Project, document: Document, offset: Int, range: TextRange): TextRange? {
  return ReadAction.computeBlocking<TextRange?, Nothing> {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@computeBlocking range
    var element: PsiElement? = psiFile.findElementAt(offset)
    if (element !is PyExpression || element is PyLiteralExpression) {
      element = PsiTreeUtil.getParentOfType(element, PyExpression::class.java)
    }
    when (element) {
      is PyReferenceExpression if element.parent is PyCallExpression -> element.textRange
      is PyCallExpression, is PyBinaryExpression, is PyPrefixExpression -> null
      else -> range
    }
  }
}
