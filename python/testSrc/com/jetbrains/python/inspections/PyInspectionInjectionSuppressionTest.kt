// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.fixtures.PyTestCase

class PyInspectionInjectionSuppressionTest : PyTestCase() {
  fun testSuppressedForInjectedIntoNonPythonHostWithoutPythonRuntime() {
    runWithoutPythonSdk {
      withInjectedPythonInto(PsiLanguageInjectionHost::class.java) {
        val file = myFixture.configureByText(
          "test.json",
          """
            {"code": "x = missing_name"}
          """.trimIndent()
        )
        val injectedElement = findInjectedElement(file)
        assertTrue(NoOpPyInspection().isSuppressedFor(injectedElement))
      }
    }
  }

  fun testNotSuppressedForInjectedIntoNonPythonHostWhenPythonRuntimeExists() {
    withInjectedPythonInto(PsiLanguageInjectionHost::class.java) {
      val file = myFixture.configureByText(
        "test.json",
        """
          {"code": "x = missing_name"}
        """.trimIndent()
      )
      val injectedElement = findInjectedElement(file)
      assertFalse(NoOpPyInspection().isSuppressedFor(injectedElement))
    }
  }

  fun testNotSuppressedForInjectedIntoPythonHostWithoutPythonRuntime() {
    runWithoutPythonSdk {
      withInjectedPythonInto(PsiLanguageInjectionHost::class.java) {
        val file = myFixture.configureByText(
          "test.py",
          "code = \"x = missing_name\""
        )
        val injectedElement = findInjectedElement(file)
        assertFalse(NoOpPyInspection().isSuppressedFor(injectedElement))
      }
    }
  }

  private fun withInjectedPythonInto(hostElementType: Class<out PsiElement>, action: () -> Unit) {
    val disposable = Disposer.newDisposable()
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myFixture.project)
    injectedLanguageManager.registerMultiHostInjector(createInjector(hostElementType), disposable)
    try {
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun createInjector(hostElementType: Class<out PsiElement>): MultiHostInjector {
    return object : MultiHostInjector {
      override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val host = context as? PsiLanguageInjectionHost ?: return
        if (!host.isValidHost) return

        registrar.startInjecting(PythonLanguage.INSTANCE)
          .addPlace(null, null, host, ElementManipulators.getValueTextRange(host))
          .doneInjecting()
      }

      override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(hostElementType)
    }
  }

  private fun findInjectedElement(file: PsiFile): PsiElement {
    val hosts = PsiTreeUtil.collectElementsOfType(file, PsiLanguageInjectionHost::class.java)
    val host = hosts.firstOrNull { it.text.contains("missing_name") } ?: error("No injection host found in test file")
    val injectedFiles = InjectedLanguageManager.getInstance(myFixture.project).getInjectedPsiFiles(host)
                        ?: error("No injected PSI files found for host")
    val injectedFile = injectedFiles.first().first as? PsiFile ?: error("Injected PSI root is not a file")
    val targetOffset = injectedFile.text.indexOf("missing_name").takeIf { it >= 0 } ?: 0
    return injectedFile.findElementAt(targetOffset) ?: injectedFile
  }

  private fun runWithoutPythonSdk(action: () -> Unit) {
    val module = myFixture.module
    val oldSdk: Sdk? = ModuleRootManager.getInstance(module).sdk
    ModuleRootModificationUtil.setModuleSdk(module, null)
    try {
      action()
    }
    finally {
      ModuleRootModificationUtil.setModuleSdk(module, oldSdk)
    }
  }

  private class NoOpPyInspection : PyInspection()
}
