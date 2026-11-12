// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ThrowableRunnable
import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.PyPsiPackageUtil
import com.jetbrains.python.codeInsight.typing.PyTypeShed.getStubRootForPackage
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import junit.framework.TestCase.assertNotNull
import org.junit.Test

class PyTensorFlowTest : PyEnvTestCase() {

  @Test
  @EnvTestTagsRequired(tags = ["tensorflow"])
  fun submoduleResolveAndCompletion() {
    runPythonTest(TensorFlowModulesTask())
  }

  private class TensorFlowModulesTask : PyExecutionFixtureTestTask("packages/tensorflow") {
    override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
      val sdk = createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS)
      val packages = PyPackageManager.getInstance(sdk).refreshAndGetPackages(false)
      assertNotNull(PyPsiPackageUtil.findPackage (packages, "tensorflow"))

      ApplicationManager.getApplication().invokeAndWait(Runnable {
        WriteAction.run<java.lang.RuntimeException?>(ThrowableRunnable {
          val modificator: SdkModificator = sdk.getSdkModificator()
          modificator.addRoot(getStubRootForPackage("tensorflow")!!, OrderRootType.CLASSES)
          modificator.commitChanges()
        })
      })

      myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
      myFixture.configureByFile("submodulesExportedAsAttributes.py")
      myFixture.checkHighlighting()
      myFixture.configureByFile("importableSubmoduleExports.py")
      myFixture.checkHighlighting()

      myFixture.configureByText("a.py", "import tensorflow.<caret>")
      assertNotNull(myFixture.completeBasic())
      UsefulTestCase.assertContainsElements(myFixture.lookupElementStrings!!, "keras", "summary", "config")
      // TODO enable it back once we fully enable Typeshed stubs for tensorflow
      //UsefulTestCase.assertDoesntContain(myFixture.lookupElementStrings!!, "optimizers", "metrics")

      myFixture.configureByText("a.py",
                                """
                                import tensorflow as tf
                                tf.<caret>
                                """.trimIndent())
      myFixture.completeBasic()
      UsefulTestCase.assertContainsElements(myFixture.lookupElementStrings!!, "keras", "summary", "config", "optimizers", "metrics")
    }
  }
}
