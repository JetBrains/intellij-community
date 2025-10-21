// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.codeInsight.completion.PythonCompletionWeigher.NOT_IMPORTED_MODULE_WEIGHT
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyType
import org.jetbrains.annotations.TestOnly

/**
 * Adds completion variants for modules and packages, inserts a dot after and calls completion on the result,
 * see [PyUnresolvedModuleAttributeCompletionContributor]
 */
class PyModuleNameCompletionContributor : CompletionContributor(), DumbAware {

  companion object {
    // temporary solution for tests that are not prepared for module name completion firing everywhere
    @TestOnly
    @JvmField
    var ENABLED = true
  }

  /**
   * Checks whether completion should be performed for a given [parameters] and delegates actual work to [doFillCompletionVariants].
   */
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!shouldDoCompletion(parameters)) return

    val otherVariants = mutableSetOf<String>()
    result.runRemainingContributors(parameters) {
      otherVariants.add(it.lookupElement.lookupString)
      result.passResult(it)
    }
    doFillCompletionVariants(parameters, result, otherVariants)
  }

  private fun doFillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet, otherVariants: Set<String>) {
    getCompletionVariants(parameters.position.parent, parameters.originalFile, otherVariants).asSequence()
      .filterIsInstance<LookupElement>()
      .filterNot { it.lookupString.startsWith('_') }
      .filter { result.prefixMatcher.isStartMatch(it) }
      .forEach { result.addElement(PrioritizedLookupElement.withPriority(it, NOT_IMPORTED_MODULE_WEIGHT.toDouble())) }
  }

  private fun getCompletionVariants(element: PsiElement, file: PsiElement, otherVariants: Set<String>): List<Any> {
    val alreadyAddedNames = HashSet<String>(otherVariants)
    val result = ArrayList<Any>()
    resolveQualifiedName(QualifiedName.fromComponents(), fromFoothold(file))
      .asSequence()
      .filterIsInstance<PsiDirectory>()
      .forEach {
        val initPy = it.findFile(PyNames.INIT_DOT_PY)
        if (initPy is PyFile) {
          val moduleType = PyModuleType(initPy)
          val context = ProcessingContext()
          context.put(PyType.CTX_NAMES, alreadyAddedNames)
          val completionVariants = moduleType.getCompletionVariants("", element, context)
          result.addAll(listOf(*completionVariants))
        }
        else {
          result.addAll(PyModuleType.getSubModuleVariants(it, element, alreadyAddedNames))
        }
      }
    return result
  }

  private fun shouldDoCompletion(parameters: CompletionParameters): Boolean {
    if (!ENABLED || PythonRuntimeService.getInstance().isInPydevConsole(parameters.originalFile)) {
      return false
    }

    val element = parameters.position
    val parent = element.parent

    val provider = element.containingFile.viewProvider
    if (provider is MultiplePsiFilesPerDocumentFileViewProvider) {
      return false
    }

    return parent is PyReferenceExpression
           && !parent.isQualified
           && parameters.originalPosition?.parent !is PyTargetExpression
           && PsiTreeUtil.getParentOfType(element, PyImportStatementBase::class.java) == null
  }
}
