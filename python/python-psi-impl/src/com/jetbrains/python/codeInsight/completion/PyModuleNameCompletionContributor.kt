// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.unresolvedReference.PyPackageAliasesProvider
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyType

/**
 * Adds completion variants for modules and packages, inserts a dot after and calls completion on the result,
 * see [PyUnresolvedModuleAttributeCompletionContributor]
 */
class PyModuleNameCompletionContributor : CompletionContributor() {
  /**
   * Checks whether completion should be performed for a given [parameters] and delegates actual work to [doFillCompletionVariants].
   */
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!shouldDoCompletion(parameters)) return
    doFillCompletionVariants(parameters, result)
  }

  fun doFillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val autoPopupController = AutoPopupController.getInstance(parameters.originalFile.project)
    val packageInsertHandler = InsertHandler<LookupElement> { context, _ ->
      // add dot for PyUnresolvedModuleAttributeCompletionContributor to work
      context.document.insertString(context.tailOffset, ".")
      context.editor.caretModel.moveToOffset(context.tailOffset)
      autoPopupController.autoPopupMemberLookup(context.editor, null)
    }
    val commonAlias = PyPackageAliasesProvider.commonImportAliases[result.prefixMatcher.prefix]
    if (commonAlias != null) {
      result.addElement(LookupElementBuilder.create(result.prefixMatcher.prefix).withTypeText(commonAlias).withInsertHandler(packageInsertHandler))
      return
    }
    getCompletionVariants(parameters.position.parent, parameters.originalFile).asSequence()
      .filterIsInstance<LookupElementBuilder>()
      .filter { result.prefixMatcher.prefixMatches(it.lookupString) }
      .filterNot { it.lookupString.startsWith('_') }
      .map {
        it.withInsertHandler(packageInsertHandler)
      }
      .forEach { result.addElement(it) }
  }

  private fun getCompletionVariants(element: PsiElement, file: PsiElement): List<Any> {
    val alreadyAddedNames = HashSet<String>()
    val result = ArrayList<Any>()
    resolveQualifiedName(QualifiedName.fromComponents(), fromFoothold(file))
      .asSequence()
      .filterIsInstance<PsiDirectory>()
      .forEach { fillCompletionVariantsFromDir(it, element, result, alreadyAddedNames) }
    return result
  }

  private fun fillCompletionVariantsFromDir(targetDir: PsiDirectory?, element: PsiElement,
                                            result: ArrayList<Any>, alreadyAddedNames: HashSet<String>) {
    if (targetDir != null) {
      val initPy = targetDir.findFile(PyNames.INIT_DOT_PY)
      if (initPy is PyFile) {
        val moduleType = PyModuleType((initPy as PyFile?)!!)
        val context = ProcessingContext()
        context.put(PyType.CTX_NAMES, alreadyAddedNames)
        val completionVariants = moduleType.getCompletionVariants("", element, context)
        result.addAll(listOf(*completionVariants))
      }
      else {
        result.addAll(PyModuleType.getSubModuleVariants(targetDir, element, alreadyAddedNames))
      }
    }
  }

  private fun shouldDoCompletion(parameters: CompletionParameters): Boolean {
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
