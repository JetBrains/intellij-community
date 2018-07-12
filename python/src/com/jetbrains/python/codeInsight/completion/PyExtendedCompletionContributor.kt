// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

/**
 * Provides basic functionality for extended completion.
 *
 * Extended code completion is actually a basic code completion that shows the names of classes, functions, modules and variables.
 *
 * To provide variants for extended completion override [doFillCompletionVariants]
 */
abstract class PyExtendedCompletionContributor : CompletionContributor() {

  protected val importingInsertHandler: InsertHandler<LookupElement> = InsertHandler { context, item ->
    addImportForLookupElement(context, item, context.tailOffset - 1)
  }

  protected val functionInsertHandler: InsertHandler<LookupElement> = object : PyFunctionInsertHandler() {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      val tailOffset = context.tailOffset - 1
      super.handleInsert(context, item)  // adds parentheses, modifies tail offset
      context.commitDocument()
      addImportForLookupElement(context, item, tailOffset)
    }
  }

  protected val stringLiteralInsertHandler: InsertHandler<LookupElement> = InsertHandler { context, item ->
    val element = item.psiElement
    if (element == null) return@InsertHandler
    if (element is PyQualifiedNameOwner) {
      insertStringLiteralPrefix(element.qualifiedName, element.name, context)
    }
    else {
      val importPath = QualifiedNameFinder.findCanonicalImportPath(element, null)
      if (importPath != null) {
        insertStringLiteralPrefix(importPath.toString(), importPath.lastComponent.toString(), context)
      }
    }
  }

  private fun insertStringLiteralPrefix(qualifiedName: String?, name: String?, context: InsertionContext) {
    if (qualifiedName != null && name != null) {
      val qualifiedNamePrefix = qualifiedName.substring(0, qualifiedName.length - name.length)
      context.document.insertString(context.startOffset, qualifiedNamePrefix)
    }
  }

  /**
   * Checks whether completion should be performed for a given [parameters] and delegates actual work to [doFillCompletionVariants].
   */
  final override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!shouldDoCompletion(parameters)) return
    doFillCompletionVariants(parameters, result)
  }

  /**
   * Subclasses should override the method to provide completion variants.
   */
  protected abstract fun doFillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet)

  private fun shouldDoCompletion(parameters: CompletionParameters): Boolean {
    if (!parameters.isExtendedCompletion) {
      return false
    }

    val element = parameters.position
    val parent = element.parent
    if (parent is PyReferenceExpression && parent.isQualified) {
      return false
    }
    if (parent is PyStringLiteralExpression) {
      val prefix = parent.text.substring(0, parameters.offset - parent.textRange.startOffset)
      if (prefix.contains(".")) {
        return false
      }
    }
    val provider = element.containingFile.viewProvider
    if (provider is MultiplePsiFilesPerDocumentFileViewProvider) {
      return false
    }

    return PsiTreeUtil.getParentOfType(element, PyImportStatementBase::class.java) == null
  }
}

private fun addImportForLookupElement(context: InsertionContext, item: LookupElement, tailOffset: Int) {
  val manager = PsiDocumentManager.getInstance(context.project)
  val document = manager.getDocument(context.file)
  if (document != null) {
    manager.commitDocument(document)
  }
  val ref = context.file.findReferenceAt(tailOffset)
  if (ref == null || ref.resolve() === item.psiElement) {
    // no import statement needed
    return
  }
  WriteCommandAction.writeCommandAction(context.project, context.file).run<RuntimeException> {
    val psiElement = item.psiElement
    if (psiElement is PsiNamedElement) {
      AddImportHelper.addImport(psiElement, context.file, ref.element as PyElement)
    }
  }
}
