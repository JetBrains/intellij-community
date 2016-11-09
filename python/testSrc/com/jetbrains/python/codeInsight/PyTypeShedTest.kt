/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.sdk.PythonSdkType
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * @author vlan
 */
@RunWith(Parameterized::class)
class PyTypeShedTest(val path: String) : PyTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor? {
    return PyTestCase.ourPy3Descriptor
  }

  @Before
  fun initialize() {
    setUp()
  }

  @After
  fun deInitialize() {
    tearDown()
  }

  @Test
  fun test() {
    EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
      with(myFixture) {
        configureByText(PythonFileType.INSTANCE, path)
        enableInspections(PyUnresolvedReferencesInspection::class.java)
        enableInspections(PyTypeCheckerInspection::class.java)
        checkHighlighting(true, false, true)
        val sdk = PythonSdkType.findPythonSdk(module)
        TestCase.assertNotNull(sdk)
      }
    })
  }

  companion object {
    @Parameterized.Parameters(name = "{0}")
    @JvmStatic fun params(): List<Array<Any>> {
      // TODO: Put real test data from typeshed here
      return listOf(
          arrayOf<Any>("foo = 10\n"),
          arrayOf<Any>("2 + 'foo'\n"))
    }
  }
}
