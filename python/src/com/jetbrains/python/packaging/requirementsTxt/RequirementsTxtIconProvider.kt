// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirementsTxt

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.requirements.RequirementsFileType
import com.jetbrains.python.sdk.PythonSdkUtil
import javax.swing.Icon

/**
 * Shows a special icon for requirements.txt that is selected as the default for any configured Python SDK.
 */
internal class RequirementsTxtIconProvider : IconProvider() {
  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    val psiFile = element as? PsiFile ?: return null
    val vFile = psiFile.virtualFile ?: return null
    if (psiFile.fileType !is RequirementsFileType) return null

    // If this file is set as the requirements.txt for any SDK in the project, show the referenced-file icon
    for (sdk in PythonSdkUtil.getAllSdks()) {
      val saved = PythonRequirementTxtSdkUtils.findRequirementsTxt(sdk) ?: continue
      if (saved == vFile) {
        return PythonIcons.Python.ReferencedFile
      }
    }
    return null
  }
}
