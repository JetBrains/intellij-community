// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.PydevConsoleRunnerFactory
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * Contributes file path references for Python string literals in the Python console.
 *
 * References are soft: used only for code completion and ignored during code inspection.
 *
 * @author vlan
 */
class PyConsoleFileReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    val pattern = psiElement(PyStringLiteralExpression::class.java).with(StringWithPathSeparatorInConsole)
    registrar.registerReferenceProvider(pattern, PyConsoleFileReferenceProvider)
  }

  /**
   * Matches string literals in a Python console that have at least one path separator in them.
   */
  object StringWithPathSeparatorInConsole : PatternCondition<PyStringLiteralExpression>("stringWithSeparatorInConsolePattern") {
    val separators = listOfNotNull("/", if (SystemInfo.isWindows) "\\" else null)

    override fun accepts(expr: PyStringLiteralExpression, context: ProcessingContext?): Boolean {
      val containingFile = expr.containingFile ?: return false
      if (!PythonRuntimeService.getInstance().isInPydevConsole(containingFile)) return false
      val stringValue = expr.stringValue
      return separators.any { it in stringValue }
    }
  }

  private object PyConsoleFileReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference> {
      val expr = element as? PyStringLiteralExpression ?: return emptyArray()
      return PyConsoleFileReferenceSet(expr, this).allReferences
    }
  }

  /**
   * A file reference in Python string literals inside the Python console.
   *
   * It provides the context for resolving paths in a Python console relative to its initial working directory.
   */
  private class PyConsoleFileReferenceSet(element: PyStringLiteralExpression, provider: PsiReferenceProvider) :
    PySoftFileReferenceContributor.PySoftFileReferenceSet(element, provider) {

    override fun computeDefaultContexts(): Collection<PsiFileSystemItem> {
      val defaultContexts = super.computeDefaultContexts()
      if (isAbsolutePathReference) return defaultContexts
      val consoleDir = getConsoleWorkingDirectory() ?: return defaultContexts
      return defaultContexts + consoleDir
    }

    private fun getConsoleWorkingDirectory(): PsiDirectory? {
      val file = containingFile ?: return null
      if (!PythonRuntimeService.getInstance().isInPydevConsole(file)) return null
      val project = file.project
      val module = ModuleUtilCore.findModuleForFile(file)
      val settingsProvider = PyConsoleOptions.getInstance(project).pythonConsoleSettings
      val workingDirPath = PydevConsoleRunnerFactory.getWorkingDir(project, module, null, settingsProvider) ?: return null
      val workingDir =  StandardFileSystems.local().findFileByPath(workingDirPath) ?: return null
      return PsiManager.getInstance(project).findDirectory(workingDir)
    }
  }
}
