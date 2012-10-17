package com.jetbrains.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.django.testRunner.DjangoTestUtil;
import com.jetbrains.django.testRunner.DjangoTestsRunConfiguration;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonCommandLineState;
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
  public MyRunProfile getRunProfile() {
    final AbstractPythonRunConfiguration configuration = (AbstractPythonRunConfiguration)getModel().getProperties().getConfiguration();
    return new MyTestRunProfile(configuration);
  }


  private class MyTestRunProfile extends MyRunProfile {

    public MyTestRunProfile(RunConfigurationBase configuration) {
      super(configuration);
    }

    @NotNull
    @Override
    public Module[] getModules() {
      return ((AbstractPythonRunConfiguration)getConfiguration()).getModules();
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
      final AbstractPythonRunConfiguration configuration = ((AbstractPythonRunConfiguration)getConfiguration());
      final PythonCommandLineState state = new FailedPythonTestCommandLineStateBase(configuration, env,
                                                            (PythonTestCommandLineStateBase)configuration.getState(executor, env));
      return state;
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
          final Location location = failedTest.getLocation(myProject);
          if (location != null) {
            final PsiElement element = location.getPsiElement();

            if (getConfiguration() instanceof DjangoTestsRunConfiguration) {
              String appName = DjangoTestUtil.getAppNameForLocation(location.getModule(), location.getPsiElement());
              String target = DjangoTestUtil.buildTargetFromLocation(appName, element);
              specs.add(target);
            }
            else {
              PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
              String path = location.getVirtualFile().getCanonicalPath();
              if (pyClass != null)
                path += "::" + pyClass.getName();
              path += "::" + failedTest.getName();
              specs.add(path);
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
}
