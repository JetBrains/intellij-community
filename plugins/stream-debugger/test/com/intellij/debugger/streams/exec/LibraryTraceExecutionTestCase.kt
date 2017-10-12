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
package com.intellij.debugger.streams.exec

import com.intellij.debugger.impl.OutputChecker
import com.intellij.debugger.streams.test.TraceExecutionTestCase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import java.io.File

/**
 * @author Vitaliy.Bibaev
 */
abstract class LibraryTraceExecutionTestCase(private val jarName: String) : TraceExecutionTestCase() {
  private val libraryDirectory = File(PluginPathManager.getPluginHomePath("stream-debugger") + "/lib").absolutePath
  override fun setUpModule() {
    super.setUpModule()
    ApplicationManager.getApplication().runWriteAction {
      VfsRootAccess.allowRootAccess(libraryDirectory)
      PsiTestUtil.addLibrary(myModule, "$libraryDirectory/$jarName")
    }
  }

  override fun initOutputChecker(): OutputChecker {
    return object : OutputChecker(testAppPath, appOutputPath) {
      override fun replaceAdditionalInOutput(str: String): String {
        return str.replaceFirst("$libraryDirectory/$jarName", "!LIBRARY_JAR!")
      }
    }
  }

  override fun createJavaParameters(mainClass: String?): JavaParameters {
    val parameters = super.createJavaParameters(mainClass)
    parameters.classPath.add("$libraryDirectory/$jarName")
    return parameters
  }

  final override fun getTestAppPath(): String {
    return File(PluginPathManager.getPluginHomePath("stream-debugger") + "/testData/${getTestAppRelativePath()}").absolutePath
  }

  abstract fun getTestAppRelativePath(): String
}