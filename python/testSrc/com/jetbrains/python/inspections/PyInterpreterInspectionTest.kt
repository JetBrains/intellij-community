package com.jetbrains.python.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.fixtures.PyTestCase

class PyInterpreterInspectionTest : PyTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor? = ourPyLatestDescriptor

  fun testNoInterpreterConfiguredShowsProblem() {
    val project = myFixture.project
    val module = myFixture.module

    val projectRootManager = ProjectRootManager.getInstance(project)
    val originalProjectSdk = projectRootManager.projectSdk
    val originalModuleSdk = ModuleRootManager.getInstance(module).sdk

    try {
      runWriteAction {
        ModuleRootModificationUtil.setModuleSdk(module, null)
        projectRootManager.projectSdk = null
      }

      val expectedMsg = PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.module")

      myFixture.configureByText("test.py", "print('hello')\n")
      myFixture.enableInspections(PyInterpreterInspection::class.java)

      val highlights = myFixture.doHighlighting()
      val warnings = highlights.filter { it.severity == HighlightSeverity.WARNING }
      assertTrue(
        "Expected to find interpreter warning produced by inspection, but got: ${warnings.map { it.description }}",
        warnings.any { it.description == expectedMsg }
      )
    }
    finally {
      runWriteAction {
        ModuleRootModificationUtil.setModuleSdk(module, originalModuleSdk)
        projectRootManager.projectSdk = originalProjectSdk
      }
    }
  }
}