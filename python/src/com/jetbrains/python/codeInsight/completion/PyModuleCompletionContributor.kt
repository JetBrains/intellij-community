// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.jetbrains.python.codeInsight.completion.PyClassNameCompletionContributor.IMPORTING_INSERT_HANDLER
import com.jetbrains.python.codeInsight.completion.PyClassNameCompletionContributor.STRING_LITERAL_INSERT_HANDLER
import com.jetbrains.python.codeInsight.imports.PythonImportUtils
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyPsiFacadeImpl
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.stubs.PyModuleNameIndex
import com.jetbrains.python.psi.types.PyModuleType

class PyModuleCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!parameters.isExtendedCompletion) return

    val targetFile = parameters.originalFile
    val inStringLiteral = parameters.position.parent is PyStringLiteralExpression
    val moduleKeys = PyModuleNameIndex.getAllKeys(targetFile.project)
    val modulesFromIndex = moduleKeys.asSequence()
      .filter { result.prefixMatcher.prefixMatches(it) }
      .flatMap { PyModuleNameIndex.find(it, targetFile.project, true).asSequence() }

    val resolveContext = PyPsiFacadeImpl(targetFile.project).createResolveContextFromFoothold(targetFile)
    val builders = modulesFromIndex
      .flatMap { resolve(it, resolveContext) }
      .filter { PythonImportUtils.isImportableModule(targetFile, it) }
      .mapNotNull { PyModuleType.buildFileLookupElement(it, null) }
      .map { it.withInsertHandler(
            if (inStringLiteral) STRING_LITERAL_INSERT_HANDLER else IMPORTING_INSERT_HANDLER)
      }

    builders.forEach { result.addElement(it) }
  }

  private fun resolve(module: PyFile, resolveContext: PyQualifiedNameResolveContext): Sequence<PyFile> {
    val qualifiedName = QualifiedNameFinder.findCanonicalImportPath(module, null) ?: return emptySequence()
    return resolveQualifiedName(qualifiedName, resolveContext).asSequence()
      .filterIsInstance<PyFile>()
  }

}