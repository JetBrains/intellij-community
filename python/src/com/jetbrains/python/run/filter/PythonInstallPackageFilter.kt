// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.filter

import com.intellij.execution.filters.Filter
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.packaging.PyPackageInstallUtils
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk

class PythonInstallPackageFilter(val project: Project, val editor: EditorImpl? = null) : Filter, DumbAware {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val prefix = "ModuleNotFoundError: No module named '"
    if (!line.startsWith(prefix))
      return null

    val moduleName = line.removePrefix(prefix).dropLastWhile { it != '\'' }.dropLast(1)
    val pipPackageName = PyPsiPackageUtil.moduleToPackageName(moduleName)
    val pythonSdk = getSdkForFile(editor) ?: project.pythonSdk ?: return null
    val existsInRepository = PyPackageInstallUtils.checkExistsInRepository(project, pythonSdk, pipPackageName)
    if (!existsInRepository)
      return null

    val info = InstallPackageButtonItem(project, pythonSdk, entireLength - line.length + "ModuleNotFoundError:".length, pipPackageName)
    return Filter.Result(
      listOf(
        info,
        // A hack without which the element will not appear.
        Filter.ResultItem(0, 0, null)
      )
    )
  }

  private fun getSdkForFile(editor: EditorImpl? = null): Sdk? {
    val document = editor?.document ?: return null
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    val viewProvider = psiFile?.viewProvider ?: return null
    val pyPsiFile = viewProvider.allFiles.firstOrNull { it is PyFile } ?: return null
    return PythonSdkUtil.findPythonSdk(pyPsiFile)
  }
}