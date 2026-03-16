// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.inspections.quickfix.InstallAndImportPackageQuickFix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@PyDefaultTestApplication
@TestClassInfo
@TestDataPath($$"$CONTENT_ROOT/../testData/quickFixes/InstallPackageQuickFixTest")
internal class InstallPackageQuickFixTest(val project: Project) {

  @Test
  fun testImportPandas(psiFile: PsiFile) = timeoutRunBlocking {
    applyInstallAndImportQuickFix(psiFile, packageName = "pandas", importAlias = null)
    assertEquals("import pandas\n\npandas.array()\n", psiFile.text)
  }

  @Test
  fun testImportPandasAsPd(psiFile: PsiFile) = timeoutRunBlocking {
    applyInstallAndImportQuickFix(psiFile, packageName = "pandas", importAlias = "pd")
    assertEquals("import pandas as pd\n\npd.array()\n", psiFile.text)
  }

  /**
   * The test currently calls onSuccess directly (bypassing applyFix / real package install),
   * because I didn't find an easy way to run the complete inspection.
   * The [com.jetbrains.python.packaging.pip.PypiPackageCache] cache is not initialized in tests
   * also [com.jetbrains.python.inspections.quickfix.InstallPackageQuickFix] is not available in batch mode and the inspection filters it out
   */
  private suspend fun applyInstallAndImportQuickFix(psiFile: PsiFile, packageName: String, importAlias: String?) {
    val descriptor = readAction {
      val element = psiFile.findElementAt(0)!!
      InspectionManager.getInstance(project)
        .createProblemDescriptor(
          element,
          "",
          null as LocalQuickFix?,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          true
        )
    }

    val quickFix = InstallAndImportPackageQuickFix(packageName, importAlias)
    quickFix.onSuccess(project, descriptor)
  }
}
