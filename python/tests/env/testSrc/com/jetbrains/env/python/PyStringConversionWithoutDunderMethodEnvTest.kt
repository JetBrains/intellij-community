// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python

import com.intellij.idea.TestFor
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.inspections.PyStringConversionWithoutDunderMethodInspection
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import org.junit.Test

/**
 * Checks [PyStringConversionWithoutDunderMethodInspection] against a real interpreter.
 *
 * A type stub omits a `__str__` and a `__repr__` that only override the `object` ones, so the inspection reads the
 * runtime module and the skeleton of a binary module. The mock SDK has neither, and only this test proves that the
 * inspection reads them. The test environment must have the `skeletons` tag.
 */
@Subsystems.Inspections
@Layers.Functional
class PyStringConversionWithoutDunderMethodEnvTest : PyEnvTestCase() {
  @Test
  @TestFor(issues = ["PY-89986", "PY-91292", "PY-89218"])
  fun testStringConversion() {
    runPythonTest(object : PyExecutionFixtureTestTask("/stringConversion/") {
      override fun getTags(): Set<String> = setOf("skeletons")

      override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
        createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_AND_SKELETONS)
        myFixture.enableInspections(PyStringConversionWithoutDunderMethodInspection::class.java)
        runInEdtAndWait {
          myFixture.configureByFile("StringConversion.py")
          myFixture.checkHighlighting(true, false, true)
        }
      }
    })
  }
}
