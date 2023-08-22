// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.search.PySearchUtilBase
import com.jetbrains.python.psi.stubs.PyModuleNameIndex

/**
 * Add completion variants for modules and packages.
 *
 * The completion contributor ensures that completion variants are resolvable with project source root configuration.
 * The list of completion variants does not include namespace packages (but includes their modules where appropriate).
 */
class PyModulePackageCompletionContributor : PyExtendedCompletionContributor() {

  override fun doFillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {

    val targetFile = parameters.originalFile
    val inStringLiteral = parameters.position.parent is PyStringLiteralExpression
    val moduleKeys = PyModuleNameIndex.getAllKeys(targetFile.project)
    val scope = PySearchUtilBase.defaultSuggestionScope(targetFile)
    val modulesFromIndex = moduleKeys.asSequence()
      .filter { result.prefixMatcher.prefixMatches(it) }
      .flatMap { PyModuleNameIndex.findByShortName(it, targetFile.project, scope).asSequence() }
      .toList()

    val resolveContext = fromFoothold(targetFile)
    val builders = modulesFromIndex.asSequence()
      .flatMap { resolve(it, resolveContext) }
      .filter { PyUtil.isImportable(targetFile, it) }
      .mapNotNull { createLookupElementBuilder(targetFile, it) }
      .map { it.withInsertHandler(
            if (inStringLiteral) stringLiteralInsertHandler else importingInsertHandler)
      }

    builders.forEach { result.addElement(it) }
  }

  private fun resolve(module: PsiFile, resolveContext: PyQualifiedNameResolveContext): Sequence<PsiFileSystemItem> {
    val qualifiedName = QualifiedNameFinder.findCanonicalImportPath(module, null) ?: return emptySequence()
    return resolveQualifiedName(qualifiedName, resolveContext).asSequence()
      .filterIsInstance<PsiFileSystemItem>()
  }

}
