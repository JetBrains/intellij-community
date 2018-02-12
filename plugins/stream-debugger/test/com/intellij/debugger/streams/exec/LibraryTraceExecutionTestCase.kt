// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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