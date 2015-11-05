/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.fixtures;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonCommandLineState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public abstract class PyCommandLineTestCase extends PyTestCase {
  private static final int PORT = 123;

  protected static int verifyPyDevDParameters(List<String> params) {
    params = Lists.newArrayList(params);
    int debugParam = params.remove("--DEBUG") ? 1 : 0;
    int qtDebugParam = params.remove("--qt-support") ? 1 : 0;
    assertEquals(PythonHelper.DEBUGGER.asParamString(), params.get(0));
    assertEquals("--multiproc", params.get(1));
    assertEquals("--client", params.get(2));
    assertEquals("--port", params.get(4));
    assertEquals("" + PORT, params.get(5));
    assertEquals("--file", params.get(6));
    return 7 + debugParam + qtDebugParam;
  }

  protected <T extends AbstractPythonRunConfiguration> T createConfiguration(final ConfigurationType configurationType, Class<T> cls) {
    final ConfigurationFactory factory = configurationType.getConfigurationFactories()[0];
    final Project project = myFixture.getProject();
    return cls.cast(factory.createTemplateConfiguration(project));
  }

  protected List<String> buildRunCommandLine(AbstractPythonRunConfiguration configuration) {
    try {
      PythonCommandLineState state = getState(configuration, DefaultRunExecutor.getRunExecutorInstance());
      assert state != null;
      return state.generateCommandLine().getParametersList().getList();
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected List<String> buildDebugCommandLine(AbstractPythonRunConfiguration configuration) {
    try {
      PythonCommandLineState state = getState(configuration, DefaultDebugExecutor.getDebugExecutorInstance());
      assert state != null;
      return state.generateCommandLine(new PyDebugRunner().createCommandLinePatchers(configuration.getProject(), state, configuration, PORT))
        .getParametersList()
        .getList();
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }


  private static PythonCommandLineState getState(@NotNull AbstractPythonRunConfiguration configuration, @NotNull Executor executor) throws ExecutionException {
    return (PythonCommandLineState)ExecutionEnvironmentBuilder.create(executor, configuration)
      .build()
      .getState();
  }
}
