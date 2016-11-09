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
import com.intellij.util.ThrowableRunnable
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * @author vlan
 */
@RunWith(Parameterized::class)
class PyTypeShedTest(val languageLevel: LanguageLevel, val path: String) : PyTestCase() {
  override fun getProjectDescriptor(): PyLightProjectDescriptor =
      if (languageLevel.isAtLeast(LanguageLevel.PYTHON30)) PyTestCase.ourPy3Descriptor else PyTestCase.ourPyDescriptor

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
        val typeShedPath = PyTypeShed.directoryPath ?: return@ThrowableRunnable
        configureByFile("$typeShedPath/$path")
        enableInspections(PyUnresolvedReferencesInspection::class.java)
        enableInspections(PyTypeCheckerInspection::class.java)
        checkHighlighting(true, false, true)
        val sdk = PythonSdkType.findPythonSdk(module)
        TestCase.assertNotNull(sdk)
      }
    })
  }

  companion object {
    val LANGUAGE_LEVELS = listOf(
        LanguageLevel.PYTHON35,
        LanguageLevel.PYTHON27)

    @Parameterized.Parameters(name = "PY{0}: {1}")
    @JvmStatic fun params(): List<Array<Any>> {
      val typeShedPath = PyTypeShed.directoryPath ?: return emptyList()
      val typeShedFile = File(typeShedPath)
      return LANGUAGE_LEVELS
          .asSequence()
          .flatMap { level ->
            PyTypeShed.findRootsForLanguageLevel(level)
                .asSequence()
                .flatMap { root ->
                  File("$typeShedPath/$root").walk()
                      .filter { it.isFile && it.extension == "pyi" }
                      .map { arrayOf(level, it.relativeTo(typeShedFile).toString()) }
                }
          }
          .toList()
    }
  }
}
