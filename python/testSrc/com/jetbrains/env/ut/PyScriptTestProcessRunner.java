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

import com.intellij.execution.configurations.ConfigurationFactory;
import com.jetbrains.env.ConfigurationBasedProcessRunner;
import com.jetbrains.env.PyAbstractTestProcessRunner;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * {@link PyAbstractTestProcessRunner} to run script-bases tests
 *
 * @author Ilya.Kazakevich
 */
public class PyScriptTestProcessRunner<CONF_T extends AbstractPythonRunConfigurationParams & AbstractPythonTestRunConfigurationParams>
  extends PyAbstractTestProcessRunner<CONF_T> {
  @NotNull
  private final String myScriptName;

  /**
   * @param scriptName name of script to run
   * @see ConfigurationBasedProcessRunner#ConfigurationBasedProcessRunner(ConfigurationFactory, Class, String)
   */
  public PyScriptTestProcessRunner(@NotNull final ConfigurationFactory configurationFactory,
                                   @NotNull final Class<CONF_T> expectedConfigurationType,
                                   @NotNull final String workingFolder,
                                   @NotNull final String scriptName,
                                   final int timesToRerunFailedTests) {
    super(configurationFactory, expectedConfigurationType, workingFolder, timesToRerunFailedTests);
    myScriptName = scriptName;
  }


  @Override
  protected void configurationCreatedAndWillLaunch(@NotNull final CONF_T configuration) throws IOException {
    super.configurationCreatedAndWillLaunch(configuration);
    configuration.setScriptName(myScriptName);
  }
}
