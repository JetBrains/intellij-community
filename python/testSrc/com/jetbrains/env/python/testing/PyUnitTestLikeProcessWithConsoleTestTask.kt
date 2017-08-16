/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.env.python.testing

import com.google.common.collect.Sets
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

  override fun getTagsToCover(): Set<String> = Sets.newHashSet("python2.6", "python2.7", "python3.5", "python3.6", "jython", "pypy",
                                                               "IronPython")


  @Throws(Exception::class)
  override fun createProcessRunner(): T =
    processRunnerCreator.apply(TestRunnerConfig(myScriptName, myRerunFailedTests))
}

data class TestRunnerConfig(val scriptName: String, val rerunFailedTests: Int)