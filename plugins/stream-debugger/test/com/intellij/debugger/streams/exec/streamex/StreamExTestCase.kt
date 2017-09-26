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
package com.intellij.debugger.streams.exec.streamex

import com.intellij.debugger.streams.exec.TraceExecutionTestCase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import java.io.File

/**
 * @author Vitaliy.Bibaev
 */
abstract class StreamExTestCase : TraceExecutionTestCase() {
  abstract protected val packageName: String

  override fun setUpModule() {
    super.setUpModule()
    ApplicationManager.getApplication().runWriteAction {
      VfsRootAccess.allowRootAccess(File("java/lib/").absolutePath)
      PsiTestUtil.addLibrary(myModule, "java/lib/streamex-0.6.5.jar")
    }
  }

  override fun createJavaParameters(mainClass: String?): JavaParameters {
    val parameters = super.createJavaParameters(mainClass)
    parameters.classPath.add(File("java/lib/streamex-0.6.5.jar").absolutePath)
    return parameters
  }

  private val className: String
    get() = packageName + "." + getTestName(false)

  override fun getTestAppPath(): String {
    return File("testData/streamex/").absolutePath
  }

  protected fun doStreamExVoidTest() = doTest(true, className)
  protected fun doStreamExWithResultTest() = doTest(false, className)
}
