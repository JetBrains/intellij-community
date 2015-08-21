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
package com.jetbrains.env.ut;

import com.jetbrains.env.ProcessWithConsoleRunner;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.testing.nosetest.PythonNoseTestRunConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ProcessWithConsoleRunner} to run py nose
 *
 * @author Ilya.Kazakevich
 */
public class PyNoseTestProcessRunner extends PyScriptTestProcessRunner<PythonNoseTestRunConfiguration> {
  public PyNoseTestProcessRunner(@NotNull final String workingFolder,
                                 @NotNull final String scriptName, final int timesToRerunFailedTests) {
    super(PythonTestConfigurationType.getInstance().PY_NOSETEST_FACTORY,
          PythonNoseTestRunConfiguration.class, workingFolder, scriptName, timesToRerunFailedTests);
  }
}
