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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkType
import icons.PythonIcons

/**
 * Various utils for custom completions
 */

/**
 * Implementation of CompletionContributor#handleAutoCompletionPossibility
 *
 * auto-insert the obvious only case; else show other cases.
 */
fun autoInsertSingleItem(context: AutoCompletionContext): AutoCompletionDecision =
  if (context.items.size == 1) {
    AutoCompletionDecision.insertItem(context.items.first())!!
  }
  else {
    AutoCompletionDecision.SHOW_LOOKUP!!
  }


fun CompletionParameters.getPyClass(): PyClass? = (ScopeUtil.getScopeOwner(position) as? PyFunction)?.containingClass
fun CompletionParameters.getFile(): PsiFile? = (ScopeUtil.getScopeOwner(position) as? PyFunction)?.containingFile


fun CompletionParameters.getTypeEvalContext(): TypeEvalContext = TypeEvalContext.codeCompletion(originalFile.project, originalFile)


/**
 * Add method completion to result.
 * @param result destination
 * @param pyClass if provided will check if method does not exist already
 * @param builderPostprocessor function to be used to tune lookup builder
 */
fun addMethodToResult(result: CompletionResultSet,
                      pyClass: PyClass?,
                      typeEvalContext: TypeEvalContext,
                      methodName: String,
                      methodParentheses: String = "(self)",
                      builderPostprocessor: ((LookupElementBuilder) -> LookupElementBuilder)? = null) {
  if (pyClass?.findMethodByName(methodName, false, typeEvalContext) != null) return

  val item = LookupElementBuilder.create(methodName + methodParentheses).withIcon(PythonIcons.Python.Nodes.Cyan_dot)
  result.addElement(TailTypeDecorator.withTail(builderPostprocessor?.invoke(item) ?: item, TailType.CASE_COLON))
}

/**
 * Add function completion to result.
 * @param result destination
 * @param pyFile if provided will check if function does not exist already
 * @param builderPostprocessor function to be used to tune lookup builder
 */
fun addFunctionToResult(result: CompletionResultSet,
                        pyFile: PyFile?,
                        functionName: String,
                        functionParentheses: String = "()",
                        builderPostprocessor: ((LookupElementBuilder) -> LookupElementBuilder)? = null) {
  if (pyFile?.findTopLevelFunction(functionName) != null) return

  val item = LookupElementBuilder.create(functionName + functionParentheses).withIcon(PythonIcons.Python.Nodes.Cyan_dot)
  result.addElement(TailTypeDecorator.withTail(builderPostprocessor?.invoke(item) ?: item, TailType.CASE_COLON))
}

/**
 * Create [LookupElementBuilder] for [element] in the [file].
 *
 * Can be used to create lookup elements for packages and modules.
 * Uses canonical import path for item representation.
 */
fun createLookupElementBuilder(file: PsiFile, element: PsiFileSystemItem): LookupElementBuilder? {
  val name = FileUtil.getNameWithoutExtension(element.name)
  if (!PyNames.isIdentifier(name)) return null

  val importPath = QualifiedNameFinder.findCanonicalImportPath(element, file)?.removeLastComponent()
  val tailText = if (importPath != null && importPath.componentCount > 0) " ($importPath)" else null

  return LookupElementBuilder.create(element, name)
    .withTailText(tailText, true)
    .withIcon(element.getIcon(0))
}
