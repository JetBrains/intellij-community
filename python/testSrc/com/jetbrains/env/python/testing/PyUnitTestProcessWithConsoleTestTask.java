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
package com.jetbrains.env.python.testing;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.PyProcessWithConsoleTestTask;
import com.jetbrains.env.ut.PyUnitTestProcessRunner;
import com.jetbrains.python.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;

/**
 * {@link PyProcessWithConsoleTestTask} to be used with python unittest. It saves you from boilerplate
 * by setting working folder and creating {@link PyUnitTestProcessRunner}
 *
 * @author Ilya.Kazakevich
 */
abstract class PyUnitTestProcessWithConsoleTestTask extends PyProcessWithConsoleTestTask<PyUnitTestProcessRunner> {
  @NotNull
  private final String myScriptName;

  PyUnitTestProcessWithConsoleTestTask(@NotNull final String relativePathToFolder, @NotNull final String scriptName) {
    super(SdkCreationType.EMPTY_SDK);
    setWorkingFolder(PyEnvTestCase.norm(getTestDataPath() + relativePathToFolder));
    myScriptName = scriptName;
  }

  @NotNull
  @Override
  protected PyUnitTestProcessRunner createProcessRunner() throws Exception {
    return new PyUnitTestProcessRunner(getWorkingFolder(), myScriptName, 0);
  }
}
