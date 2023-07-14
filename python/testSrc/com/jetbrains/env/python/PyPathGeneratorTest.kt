// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python

import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyExecutionFixtureTestTask
import com.jetbrains.python.run.PythonCommandLineState
import com.jetbrains.python.run.collectPythonPath
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import junit.framework.TestCase.*
import org.junit.Test

class PyPathGeneratorTest : PyEnvTestCase() {

  @Test
  fun `test only source dirs are added to PYTHONPATH`() {
    runPythonTest(object : PyExecutionFixtureTestTask(null) {
      override fun runTestOn(sdkHome: String, existingSdk: Sdk?) {
        val sdk = existingSdk ?: createTempSdk(sdkHome, SdkCreationType.EMPTY_SDK)
        val module = myFixture.module
        module.pythonSdk = sdk

        var myFile: VirtualFile? = null
        var myDir: VirtualFile? = null

        runWriteActionAndWait {
          ModuleRootManager.getInstance(module).modifiableModel.apply {
            contentEntries[0].apply {
              myFile = file!!.createChildData(this@PyPathGeneratorTest, "my_file.txt")
              myDir = file!!.createChildDirectory(this@PyPathGeneratorTest, "my_dir")
              assertNotNull(myFile)
              assertNotNull(myDir)
              addSourceFolder(myFile!!, false) // add file as a source root
              addSourceFolder(myDir!!, false) // add directory as a source root
            }
            commit()
          }
        }

        fun assertContains(expected: Boolean = true, paths: Collection<String>, file: VirtualFile) {
          val independentPaths = paths.map { FileUtil.toSystemIndependentName(it) }
          assertNotNull(file.canonicalPath)
          val expectedPath = FileUtil.toSystemIndependentName(file.canonicalPath!!)
          if (expected) {
            UsefulTestCase.assertContainsElements(independentPaths, expectedPath)
          }
          else {
            UsefulTestCase.assertDoesntContain(independentPaths, expectedPath)
          }
        }

        // Old (pre-target) API
        var paths = PythonCommandLineState.collectPythonPath(module, sdkHome, true, true, false)
        assertContains(expected = false, paths = paths, file = myFile!!)
        assertContains(paths = paths, file = myDir!!)

        // New (target) API
        val env = LocalTargetEnvironmentRequest().prepareEnvironment(TargetProgressIndicator.EMPTY)
        paths = collectPythonPath(myFixture.project, module, sdkHome, null, true, true, false)
          .map { it.apply(env) }
        assertContains(expected = false, paths = paths, file = myFile!!)
        assertContains(paths = paths, file = myDir!!)
      }
    })
  }
}