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
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * {@link ProcessWithConsoleRunner} to run doctest
 *
 * @author Ilya.Kazakevich
 */
public class PyDocTestProcessRunner extends PyScriptTestProcessRunner<PythonDocTestRunConfiguration> {
  public PyDocTestProcessRunner(@NotNull final String scriptName, final int timesToRerunFailedTests) {
    super(PythonTestConfigurationType.getInstance().getDocTestFactory(),
          PythonDocTestRunConfiguration.class, scriptName, timesToRerunFailedTests);
    setSkipExitCodeAssertion(true);    //Doctest doesn't support exit codes
  }

  @Override
  protected void configurationCreatedAndWillLaunch(@NotNull final PythonDocTestRunConfiguration configuration) throws IOException {
    super.configurationCreatedAndWillLaunch(configuration);
    configuration.setInterpreterOptions(configuration.getInterpreterOptions() + " -W error");
  }
}
