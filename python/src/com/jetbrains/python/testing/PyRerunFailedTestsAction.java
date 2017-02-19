/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.CommandLinePatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class PyRerunFailedTestsAction extends AbstractRerunFailedTestsAction {
  protected PyRerunFailedTestsAction(@NotNull ComponentContainer componentContainer) {
    super(componentContainer);
  }

  @Override
  @Nullable
  protected MyRunProfile getRunProfile(@NotNull ExecutionEnvironment environment) {
    final TestFrameworkRunningModel model = getModel();
    if (model == null) {
      return null;
    }
    return new MyTestRunProfile((AbstractPythonRunConfiguration)model.getProperties().getConfiguration());
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
      final AbstractPythonTestRunConfiguration<?> configuration = ((AbstractPythonTestRunConfiguration)getPeer());

      // If configuration wants to take care about rerun itself
      if (configuration instanceof TestRunConfigurationReRunResponsible) {
        // TODO: Extract method
        final Set<PsiElement> failedTestElements = new HashSet<>();
        for (final AbstractTestProxy proxy : getFailedTests(getProject())) {
          final Location<?> location = proxy.getLocation(getProject(), GlobalSearchScope.allScope(getProject()));
          if (location != null) {
            failedTestElements.add(location.getPsiElement());
          }
        }
        return ((TestRunConfigurationReRunResponsible)configuration).rerunTests(executor, env, failedTestElements);
      }
      return new FailedPythonTestCommandLineStateBase(configuration, env,
                                                      (PythonTestCommandLineStateBase)configuration.getState(executor, env));
    }
  }

  private class FailedPythonTestCommandLineStateBase extends PythonTestCommandLineStateBase<AbstractPythonTestRunConfiguration<?>> {
    private final PythonTestCommandLineStateBase<?> myState;
    private final Project myProject;

    public FailedPythonTestCommandLineStateBase(AbstractPythonTestRunConfiguration<?> configuration,
                                                ExecutionEnvironment env,
                                                PythonTestCommandLineStateBase<?> state) {
      super(configuration, env);
      myState = state;
      myProject = configuration.getProject();
    }

    @Override
    protected HelperPackage getRunner() {
      return myState.getRunner();
    }

    @Nullable
    @Override
    protected SMTestLocator getTestLocator() {
      return myState.getTestLocator();
    }

    @Override
    public ExecutionResult execute(Executor executor, PythonProcessStarter processStarter, CommandLinePatcher... patchers) throws ExecutionException {
      // Insane rerun tests with out of spec.
      if (getTestSpecs().isEmpty()) {
        throw new ExecutionException(PyBundle.message("runcfg.tests.cant_rerun"));
      }
      return super.execute(executor, processStarter, patchers);
    }

    @NotNull
    @Override
    protected List<String> getTestSpecs() {

      final List<Pair<Location<?>, AbstractTestProxy>> failedTestLocations = new ArrayList<>();
      final List<AbstractTestProxy> failedTests = getFailedTests(myProject);
      for (final AbstractTestProxy failedTest : failedTests) {
        if (failedTest.isLeaf()) {
          final Location<?> location = failedTest.getLocation(myProject, myConsoleProperties.getScope());
          if (location != null) {
            failedTestLocations.add(Pair.create(location, failedTest));
          }
        }
      }

      final List<String> result;
      final AbstractPythonTestRunConfiguration<?> configuration = getConfiguration();
      if (configuration instanceof PyRerunAwareConfiguration) {
        result = ((PyRerunAwareConfiguration)configuration).getTestSpecsForRerun(myConsoleProperties.getScope(), failedTestLocations);
      }
      else {
        final Collection<String> locations = new LinkedHashSet<>();
        locations.addAll(failedTestLocations.stream()
                           .map(o -> configuration.getTestSpec(o.first, o.second))
                           .filter(o -> o != null)
                           .collect(Collectors.toList()));
        result = new ArrayList<>(locations);
      }

      if (result.isEmpty()) {
        final List<String> locations = failedTests.stream().map(AbstractTestProxy::getLocationUrl).collect(Collectors.toList());
        Logger.getInstance(FailedPythonTestCommandLineStateBase.class).warn(
          String.format("Can't resolve specs for the following tests: %s", StringUtil.join(locations, ", ")));
      }
      return result;
    }

    @Override
    protected void addAfterParameters(GeneralCommandLine cmd) {
      myState.addAfterParameters(cmd);
    }

    @Override
    protected void addBeforeParameters(GeneralCommandLine cmd) {
      myState.addBeforeParameters(cmd);
    }

    @Override
    public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
      myState.customizeEnvironmentVars(envs, passParentEnvs);
    }
  }
}