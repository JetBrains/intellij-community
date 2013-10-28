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
package com.jetbrains.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * User: ktisha
 */
public class PyRerunFailedTestsAction extends AbstractRerunFailedTestsAction {

  protected PyRerunFailedTestsAction(@NotNull ComponentContainer componentContainer) {
    super(componentContainer);
  }

  @Override
  @Nullable
  public MyRunProfile getRunProfile() {
    final TestFrameworkRunningModel model = getModel();
    if (model == null) return null;
    final AbstractPythonRunConfiguration configuration = (AbstractPythonRunConfiguration)model.getProperties().getConfiguration();
    return new MyTestRunProfile(configuration);
  }


  private class MyTestRunProfile extends MyRunProfile {

    public MyTestRunProfile(RunConfigurationBase configuration) {
      super(configuration);
    }

    @NotNull
    @Override
    public Module[] getModules() {
      return ((AbstractPythonRunConfiguration)getPeer()).getModules();
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
      final AbstractPythonRunConfiguration configuration = ((AbstractPythonRunConfiguration)getPeer());
      return new FailedPythonTestCommandLineStateBase(configuration, env,
                                                            (PythonTestCommandLineStateBase)configuration.getState(executor, env));
    }
  }

  private class FailedPythonTestCommandLineStateBase extends PythonTestCommandLineStateBase {

    private final PythonTestCommandLineStateBase myState;
    private final Project myProject;

    public FailedPythonTestCommandLineStateBase(AbstractPythonRunConfiguration configuration,
                                                ExecutionEnvironment env,
                                                PythonTestCommandLineStateBase state) {
      super(configuration, env);
      myState = state;
      myProject = configuration.getProject();
    }

    @Override
    protected String getRunner() {
      return myState.getRunner();
    }

    @Override
    protected List<String> getTestSpecs() {
      List<String> specs = new ArrayList<String>();
      List<AbstractTestProxy> failedTests = getFailedTests(myProject);
      for (AbstractTestProxy failedTest : failedTests) {
        if (failedTest.isLeaf()) {
          final Location location = failedTest.getLocation(myProject, myConsoleProperties.getScope());
          if (location != null) {
            String spec = getConfiguration().getTestSpec(location);
            if (spec != null && !specs.contains(spec)) {
              specs.add(spec);
            }
          }
        }
      }
      return specs;
    }

    @Override
    protected void addAfterParameters(GeneralCommandLine cmd) throws ExecutionException {
      myState.addAfterParameters(cmd);
    }

    @Override
    protected void addBeforeParameters(GeneralCommandLine cmd) throws ExecutionException {
      myState.addBeforeParameters(cmd);
    }

    @Override
    public void addPredefinedEnvironmentVariables(Map<String, String> envs, boolean passParentEnvs) {
      myState.addPredefinedEnvironmentVariables(envs,
                                                passParentEnvs);
    }
  }

  @NotNull
  @Override
  protected Filter getFilter(Project project, GlobalSearchScope searchScope) {
    return new Filter() {
      public boolean shouldAccept(final AbstractTestProxy test) {
        boolean ignored = (test.getMagnitude() == TestStateInfo.Magnitude.IGNORED_INDEX.getValue());
        return !ignored && (test.isInterrupted() || test.isDefect());
      }
    };
  }
}
