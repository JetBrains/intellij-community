/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.TailType
import com.intellij.codeInsight.completion.AutoCompletionContext
import com.intellij.codeInsight.completion.AutoCompletionDecision
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.TailTypeDecorator
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import icons.PythonIcons

/**
 * Various utils for custom completions
 */

/**
 * Implementation of CompletionContributor#handleAutoCompletionPossibility
 *
 * auto-insert the obvious only case; else show other cases.
 */
fun autoInsertSingleItem(context: AutoCompletionContext) =
  if (context.items.size == 1) {
    AutoCompletionDecision.insertItem(context.items.first())!!
  }
  else {
    AutoCompletionDecision.SHOW_LOOKUP!!
  }


fun CompletionParameters.getPyClass() = (ScopeUtil.getScopeOwner(position) as? PyFunction)?.containingClass
fun CompletionParameters.getFile() = (ScopeUtil.getScopeOwner(position) as? PyFunction)?.containingFile


fun CompletionParameters.getTypeEvalContext() = TypeEvalContext.codeCompletion(originalFile.project, originalFile)


/**
 * Add method completion to to result.
 * @param result destination
 * @param element if provided will check if method does not exist already
 * @param builderPostprocessor function to be used to tune lookup builder
 */
fun addMethodToResult(result: CompletionResultSet,
                      element: PyTypedElement?,
                      typeEvalContext: TypeEvalContext,
                      methodName: String,
                      methodParentheses: String = "(self)",
                      builderPostprocessor: ((LookupElementBuilder) -> LookupElementBuilder)? = null) {
  val type = if (element != null) typeEvalContext.getType(element) else null
  if (type != null) {
    val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(typeEvalContext)

    if (type.resolveMember(methodName, null, AccessDirection.READ, resolveContext)?.firstOrNull()?.element != null) {
      return
    }
  }

  val item = LookupElementBuilder.create(methodName + methodParentheses)
    .withIcon(PythonIcons.Python.Nodes.Cyan_dot)
  result.addElement(TailTypeDecorator.withTail(builderPostprocessor?.invoke(item) ?: item, TailType.CASE_COLON))
}