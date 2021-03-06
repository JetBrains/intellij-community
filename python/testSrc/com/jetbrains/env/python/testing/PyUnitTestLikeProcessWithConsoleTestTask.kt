// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.env.python.testing

import com.jetbrains.env.PyProcessWithConsoleTestTask
import com.jetbrains.env.ut.PyScriptTestProcessRunner
import com.jetbrains.env.ut.PyUnitTestProcessRunner
import com.jetbrains.python.tools.sdkTools.SdkCreationType
import java.util.function.Function

/**
 * [PyProcessWithConsoleTestTask] to be used with python unittest and trial. It saves you from boilerplate
 * by setting working folder and creating [PyUnitTestProcessRunner]

 * @author Ilya.Kazakevich
 */
internal abstract class PyUnitTestLikeProcessWithConsoleTestTask<T :
PyScriptTestProcessRunner<*>> @JvmOverloads constructor(relativePathToTestData: String,
                                                        val myScriptName: String,
                                                        val myRerunFailedTests: Int = 0,
                                                        protected val processRunnerCreator: Function<TestRunnerConfig, T>) :
  PyProcessWithConsoleTestTask<T>(relativePathToTestData, SdkCreationType.SDK_PACKAGES_ONLY) {

  override fun getTagsToCover(): Set<String> = hashSetOf("python2.6", "python2.7", "python3.5", "python3.6", "jython", "pypy",
                                                         "IronPython")


  @Throws(Exception::class)
  override fun createProcessRunner(): T =
    processRunnerCreator.apply(TestRunnerConfig(myScriptName, myRerunFailedTests))
}

data class TestRunnerConfig(val scriptName: String, val rerunFailedTests: Int) {
  fun increaseRerunCount(rerunFailedTests: Int) = copy(scriptName = scriptName, rerunFailedTests = rerunFailedTests)
}
