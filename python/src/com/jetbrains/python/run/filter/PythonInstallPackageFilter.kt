// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.filter

import com.intellij.execution.filters.Filter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.launchInstallPackageWithBalloonBackground
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.isNotInstalledAndCanBeInstalled
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.isReadOnly
import org.jetbrains.annotations.ApiStatus

class PythonInstallPackageFilter(val project: Project, var editor: EditorImpl? = null) : Filter {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val packageName = getInstallablePackageName(project, editor, line) ?: return null
    val info = InstallPackageButtonItem(project, editor, entireLength, packageName)
    
    return Filter.Result(
      listOf(
        info,
        // A hack without which the element will not appear.
        Filter.ResultItem(0, 0, null)
      )
    )
  }

  @ApiStatus.Internal
  override fun isDumbAware(): Boolean = true

  @ApiStatus.Internal
  companion object {
    private fun getInterpreterForFile(project: Project, editor: Editor? = null): PythonInterpreter? {
      val document = editor?.document ?: return null
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
      val viewProvider = psiFile?.viewProvider ?: return null
      val pyPsiFile = viewProvider.allFiles.firstOrNull { it is PyFile } ?: return null
      return pyPsiFile.findEvoPyProjectIfReady(mainForOrphans = false)?.interpreter
    }

    /** The interpreter of the file in [editor], else the one of the main Python project. */
    private fun getInterpreter(project: Project, editor: Editor? = null): PythonInterpreter? {
      return runReadAction { getInterpreterForFile(project, editor) } ?: project.findMainEvoPyProjectIfReady()?.interpreter
    }

    /**
     * Installs [packageName] into the interpreter of the file in [editor], and shows the result in a balloon at [point].
     * Does nothing when there is no interpreter.
     */
    fun launchInstall(project: Project, editor: Editor?, packageName: String, point: RelativePoint) {
      val interpreter = getInterpreter(project, editor) ?: return
      PythonPackageManagerUI.forPythonInterpreter(project, interpreter).launchInstallPackageWithBalloonBackground(packageName, point)
    }

    /**
     * If a line contains ModuleNotFoundError: No module named 'moduleName',
     * then checks if the package can be installed and returns the package name or null.
     */
    fun getInstallablePackageName(project: Project, editor: EditorImpl?, line: String): String? {
      val prefix = "ModuleNotFoundError: No module named '"
      if (!line.startsWith(prefix))
        return null

      val moduleName = line.removePrefix(prefix).dropLastWhile { it != '\'' }.dropLast(1)
      val interpreter = getInterpreter(project, editor) ?: return null

      val packageManager = PythonPackageManager.forPythonInterpreter(project, interpreter)

      val packageName = PyPsiPackageUtil.moduleToPackageName(moduleName)
      val isCanBeInstalled = !interpreter.isReadOnly && packageManager.isNotInstalledAndCanBeInstalled(packageName)
      if (!isCanBeInstalled)
        return null

      return packageName
    }
  }
}