// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.tensorFlow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PyTensorFlowTest : PyEnvTestCase() {

  @Test
  @EnvTestTagsRequired(tags = ["tensorflow1"])
  fun tensorFlow1Modules() {
    runPythonTest(TensorFlowModulesTask("tensorflow", "v1modules.txt"))
  }

  @Test
  @EnvTestTagsRequired(tags = ["tensorflow2"])
  fun tensorFlow2Modules() {
    runPythonTest(TensorFlowModulesTask("tensorflow", "v2modules.txt"))
  }

  @Test
  @EnvTestTagsRequired(tags = ["tensorflow2"])
  fun compatTensorFlow1Modules() {
    runPythonTest(TensorFlowModulesTask("tensorflow.compat.v1", "compatv1modules.txt"))
  }

  private class TensorFlowModulesTask(private val prefix: String, private val fileName: String) : PyExecutionFixtureTestTask(null) {

    override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
      createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS)

      val modules = Files.readAllLines(Paths.get(PythonTestUtil.getTestDataPath(), "tensorflow", fileName))
      runCompletion(modules)
      runResolve(modules)
    }

    private fun runCompletion(modules: List<String>) {
      // `from tensorflow.<m>` completes
      // `tensorflow.<m>` completes
      configureAndCompleteAtCaret("from $prefix.<caret>", modules)
      configureAndCompleteAtCaret("import $prefix\n$prefix.<caret>", modules)
    }

    private fun runResolve(modules: List<String>) {
      // `from tensorflow.<m>` resolves
      // `tensorflow.<m>` resolves
      // `from tensorflow.<m>` resolves to the same as `tensorflow.<m>`

      modules.forEach {
        UsefulTestCase.assertSame(
          configureAndResolveAtCaret("import $prefix.$it<caret>"),
          configureAndResolveAtCaret("from $prefix.$it<caret> import *")
        )
      }
    }

    private fun configureAndCompleteAtCaret(text: String, modules: List<String>) {
      myFixture.configureByText(PythonFileType.INSTANCE, text)
      myFixture.completeBasic()
      UsefulTestCase.assertContainsElements(myFixture.lookupElementStrings!!, modules)
    }

    private fun configureAndResolveAtCaret(text: String): PsiElement {
      myFixture.configureByText(PythonFileType.INSTANCE, text)
      return ApplicationManager.getApplication().runReadAction(
        Computable { (myFixture.file.findElementAt(myFixture.caretOffset - 1)!!.parent as PyReferenceExpression).reference.resolve()!! }
      )
    }
  }
}