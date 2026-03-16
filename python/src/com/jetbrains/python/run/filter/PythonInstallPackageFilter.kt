// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.filter

import com.intellij.execution.filters.Filter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.isNotInstalledAndCanBeInstalled
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.isReadOnly
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.annotations.ApiStatus

class PythonInstallPackageFilter(val project: Project, var editor: EditorImpl? = null) : Filter {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {

    val (pythonSdk, packageName) = getInstallablePackageName(project, editor, line) ?: return null

    val info = InstallPackageButtonItem(project, pythonSdk, entireLength - line.length + "ModuleNotFoundError:".length, packageName)
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
    private fun getSdkForFile(project: Project, editor: Editor? = null): Sdk? {
      val document = editor?.document ?: return null
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
      val viewProvider = psiFile?.viewProvider ?: return null
      val pyPsiFile = viewProvider.allFiles.firstOrNull { it is PyFile } ?: return null
      return PythonSdkUtil.findPythonSdk(pyPsiFile)
    }

    fun getSdk(project: Project, editor: Editor? = null): Sdk? {
      return runReadAction { getSdkForFile(project, editor) } ?: project.pythonSdk ?: project.modules.firstNotNullOfOrNull { it.pythonSdk }
    }

    /**
     * If a line contains ModuleNotFoundError: No module named 'moduleName',
     * then checks if the package can be installed and returns a pair of sdk and package name or null.
     */
    fun getInstallablePackageName(project: Project, editor: EditorImpl?, line: String): Pair<Sdk, String>? {
      val prefix = "ModuleNotFoundError: No module named '"
      if (!line.startsWith(prefix))
        return null

      val moduleName = line.removePrefix(prefix).dropLastWhile { it != '\'' }.dropLast(1)
      val pythonSdk = getSdk(project, editor) ?: return null

      val packageManager = PythonPackageManager.forSdk(project, pythonSdk)

      val packageName = PyPsiPackageUtil.moduleToPackageName(moduleName)
      val isCanBeInstalled = !pythonSdk.isReadOnly && packageManager.isNotInstalledAndCanBeInstalled(packageName)
      if (!isCanBeInstalled)
        return null

      return pythonSdk to packageName
    }
  }
}