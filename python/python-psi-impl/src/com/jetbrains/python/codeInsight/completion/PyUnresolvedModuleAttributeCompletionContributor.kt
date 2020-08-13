// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.stubs.PyModuleNameIndex


/**
 * Adds completion variants for unresolved module after dot.
 */
class PyUnresolvedModuleAttributeCompletionContributor : CompletionContributor() {
  /**
   * Checks whether completion should be performed for a given [parameters] and delegates actual work to [doFillCompletionVariants].
   */
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!shouldDoCompletion(parameters)) return
    doFillCompletionVariants(parameters, result)
  }

  fun doFillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {

    val element = parameters.position
    val parent = element.parent
    if (parent !is PyReferenceExpression || !parent.isQualified) {
      return
    }

    val qualifier = parent.qualifier!!.name ?: return
    val prefixMatcher = PlainPrefixMatcher(qualifier)

    val targetFile = parameters.originalFile
    val moduleKeys = PyModuleNameIndex.getAllKeys(targetFile.project)
    val modulesFromIndex = moduleKeys.asSequence()
      .filter { prefixMatcher.prefixMatches(it) }
      .flatMap { PyModuleNameIndex.find(it, targetFile.project, true).asSequence() }
      .toList()

    val builders = modulesFromIndex.asSequence()
      .filter { PyUtil.isImportable(targetFile, it) }
      .flatMap { it.iterateNames().asSequence() }
      .filter { it !is PsiFileSystemItem && it.name != null }
      .mapNotNull {
        val lookupString = if (it is PyQualifiedNameOwner && it.qualifiedName != null) it.qualifiedName!! else it.name!!
        LookupElementBuilder.create(it, lookupString)
          .withIcon(it.getIcon(0))
      }
      .map { it.withInsertHandler(importingInsertHandler) }

    val newResultSet = result.withPrefixMatcher(PlainPrefixMatcher("$qualifier."))
    builders.forEach { newResultSet.addElement(it) }
  }

  private val importingInsertHandler: InsertHandler<LookupElement> = InsertHandler { context, item ->
    addImportForLookupElement(context, item, context.tailOffset - 1)
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
      if (psiElement is PsiNamedElement && psiElement.containingFile != null) {
        val fileName = psiElement.containingFile.name
        val elementNameQualifier = if (psiElement is PyQualifiedNameOwner) psiElement.qualifiedName?.substringBeforeLast('.') else null
        val nameToImport = elementNameQualifier ?: fileName.substringBefore(".py")
        AddImportHelper.addImportStatement(context.file, nameToImport, null, null, ref.element as PyElement)
      }
    }
  }

  private fun shouldDoCompletion(parameters: CompletionParameters): Boolean {
    val element = parameters.position
    val parent = element.parent
    if (parent is PyReferenceExpression && parent.isQualified && PsiTreeUtil.getParentOfType(element,
                                                                                             PyImportStatementBase::class.java) == null) {
      return PyResolveUtil.resolveLocally(parent).isEmpty()
    }
    return false
  }
}
