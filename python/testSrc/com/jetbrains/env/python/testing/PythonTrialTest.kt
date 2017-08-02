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
package com.jetbrains.env.python.testing

import com.jetbrains.env.EnvTestTagsRequired
import com.jetbrains.env.ut.PyScriptTestProcessRunner
import com.jetbrains.python.testing.PyTrialTestConfiguration
import com.jetbrains.python.testing.PyTrialTestFactory

// Twisted trial test case
@EnvTestTagsRequired(tags = arrayOf("twisted"))
internal class PythonTrialTest : PythonUnitTestingLikeTest<PyTrialTestProcessRunner>() {
  override fun createTestRunner(config: TestRunnerConfig) = PyTrialTestProcessRunner(config.scriptName, config.rerunFailedTests)
}


class PyTrialTestProcessRunner(scriptName: String,
                               timesToRerunFailedTests: Int) : PyScriptTestProcessRunner<PyTrialTestConfiguration>(
  PyTrialTestFactory, PyTrialTestConfiguration::class.java, scriptName, timesToRerunFailedTests)
