// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.typeshed

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestApplicationManager
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
import java.io.InputStreamReader

/**
 * @author vlan
 */
@RunWith(Parameterized::class)
class PyTypeShedTestDataTest(path: String, sdkPath: String) : PyTypeShedTestCase(path, sdkPath) {
  @Test
  fun test() {
    EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
      val fullPath = "$testDataPath/$path"
      runProcess(sdkPath, "-m", "pytest", fullPath)
      val importablePath = path.split("/").drop(2).joinToString("/")
      fixture?.copyFileToProject(fullPath, importablePath)
      fixture?.configureFromTempProjectFile(importablePath)
      fixture?.enableInspections(PyUnresolvedReferencesInspection::class.java)
      fixture?.enableInspections(PyTypeCheckerInspection::class.java)
      fixture?.checkHighlighting(true, false, true)
      val moduleSdk = PythonSdkUtil.findPythonSdk(fixture?.module)
      TestCase.assertNotNull(moduleSdk)
    })
  }

  private fun runProcess(vararg args: String) {
    val process = GeneralCommandLine(args.asList()).createProcess()
    process.waitFor()
    val stderr = InputStreamReader(process.errorStream).readText()
    val stdout = InputStreamReader(process.inputStream).readText()
    TestCase.assertEquals(if (stderr.isEmpty()) stdout else stderr, 0, process.exitValue())
  }

  companion object {
    @Parameterized.Parameters(name = "{0}: {1}")
    @JvmStatic fun params(): List<Array<Any>> {
      TestApplicationManager.getInstance()
      val testDataFile = File(testDataPath)
      return getSdkPaths()
        .asSequence()
        .flatMap { sdkPath ->
          val level = getLanguageLevel(sdkPath) ?: return@flatMap emptySequence<Array<Any>>()
          PyTypeShed.findRootsForLanguageLevel(level).asSequence()
            .flatMap { root: String ->
              File("$testDataPath/$root").walk()
                .filter { it.isFile && it.path.matches(Regex(".*_test.py")) }
                .map { arrayOf<Any>(it.relativeTo(testDataFile).toString(), sdkPath) }
            }
        }
        .toList()
    }

    private val testDataPath: String
      get() = "${PlatformTestUtil.getCommunityPath()}/python/testData/typeshed"
  }
}
