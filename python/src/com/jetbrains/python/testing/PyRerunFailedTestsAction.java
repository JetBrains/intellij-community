// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
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
import com.jetbrains.python.run.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
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
    return new MyTestRunProfile((AbstractPythonRunConfiguration<?>)model.getProperties().getConfiguration());
  }

  private class MyTestRunProfile extends MyRunProfile {

    MyTestRunProfile(RunConfigurationBase configuration) {
      super(configuration);
    }

    @Override
    public Module @NotNull [] getModules() {
      return ((AbstractPythonRunConfiguration<?>)getPeer()).getModules();
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
      final AbstractPythonTestRunConfiguration<?> configuration = ((AbstractPythonTestRunConfiguration<?>)getPeer());

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
                                                      (PythonTestCommandLineStateBase<?>)configuration.getState(executor, env));
    }
  }

  private class FailedPythonTestCommandLineStateBase extends PythonTestCommandLineStateBase<AbstractPythonTestRunConfiguration<?>> {
    private final PythonTestCommandLineStateBase<?> myState;
    private final Project myProject;

    FailedPythonTestCommandLineStateBase(AbstractPythonTestRunConfiguration<?> configuration,
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
    public ExecutionResult execute(Executor executor, PythonProcessStarter processStarter, CommandLinePatcher... patchers)
      throws ExecutionException {
      // Insane rerun tests with out of spec.
      if (getTestSpecs().isEmpty()) {
        throw new ExecutionException(PyBundle.message("runcfg.tests.cant_rerun"));
      }
      return super.execute(executor, processStarter, patchers);
    }

    @Override
    public @NotNull ExecutionResult execute(Executor executor, @NotNull PythonScriptTargetedCommandLineBuilder converter)
      throws ExecutionException {
      // Insane rerun tests with out of spec.
      if (getTestSpecs().isEmpty()) {
        throw new ExecutionException(PyBundle.message("runcfg.tests.cant_rerun"));
      }
      return super.execute(executor, converter);
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
        result = failedTestLocations.stream()
          .map(o -> configuration.getTestSpec(o.first, o.second))
          .filter(Objects::nonNull).distinct().collect(Collectors.toList());
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
    protected void addBeforeParameters(@NotNull PythonScriptExecution testScriptExecution) {
      myState.addBeforeParameters(testScriptExecution);
    }

    @Override
    protected void addAfterParameters(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                      @NotNull PythonScriptExecution testScriptExecution) {
      myState.addAfterParameters(targetEnvironmentRequest, testScriptExecution);
    }

    @Override
    public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
      super.customizeEnvironmentVars(envs, passParentEnvs);
      myState.customizeEnvironmentVars(envs, passParentEnvs);
    }

    @Override
    public void customizePythonExecutionEnvironmentVars(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                                        @NotNull Map<String, Function<TargetEnvironment, String>> envs,
                                                        boolean passParentEnvs) {
      super.customizePythonExecutionEnvironmentVars(targetEnvironmentRequest, envs, passParentEnvs);
      myState.customizePythonExecutionEnvironmentVars(targetEnvironmentRequest, envs, passParentEnvs);
    }
  }
}
