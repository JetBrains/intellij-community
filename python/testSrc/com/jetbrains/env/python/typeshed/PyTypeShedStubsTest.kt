/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.env.python.typeshed

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ThrowableRunnable
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.sdk.PythonSdkUtil
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * @author vlan
 */
@RunWith(Parameterized::class)
class PyTypeShedStubsTest(path: String, sdkPath: String) : PyTypeShedTestCase(path, sdkPath) {
  @Test
  fun test() {
    EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
      val typeShedPath = PyTypeShed.directoryPath ?: return@ThrowableRunnable
      val importablePath = path.split("/").drop(2).joinToString("/")
      fixture?.copyFileToProject("$typeShedPath/$path", importablePath)
      fixture?.configureFromTempProjectFile(importablePath)
      fixture?.enableInspections(PyUnresolvedReferencesInspection::class.java)
      fixture?.enableInspections(PyTypeCheckerInspection::class.java)
      fixture?.checkHighlighting(true, false, true)
      val moduleSdk = PythonSdkUtil.findPythonSdk(fixture?.module)
      TestCase.assertNotNull(moduleSdk)
    })
  }

  companion object {
    @Parameterized.Parameters(name = "{0}: {1}")
    @JvmStatic fun params(): List<Array<Any>> {
      LightPlatformTestCase.initApplication()
      val typeShedPath = PyTypeShed.directoryPath ?: return emptyList()
      val typeShedFile = File(typeShedPath)
      return getSdkPaths()
        .asSequence()
        .flatMap { sdkPath ->
          val level = getLanguageLevel(sdkPath) ?: return@flatMap emptySequence<Array<Any>>()
          PyTypeShed.findRootsForLanguageLevel(level).asSequence()
            .flatMap { root: String ->
              File("$typeShedPath/$root").walk()
                .filter { it.isFile && it.extension == "pyi" && "third_party" !in it.absolutePath }
                .map { arrayOf<Any>(it.relativeTo(typeShedFile).toString(), sdkPath) }
            }
        }
        .toList()
    }
  }
}
