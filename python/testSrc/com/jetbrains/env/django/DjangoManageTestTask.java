package com.jetbrains.env.django;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.django.facet.DjangoFacet;
import com.jetbrains.django.fixtures.DjangoTestCase;
import com.jetbrains.django.manage.DjangoManageTask;
import com.jetbrains.django.testRunner.DjangoTestsConfigurationType;
import com.jetbrains.django.ui.DjangoBundle;
import com.jetbrains.env.python.debug.PyExecutionFixtureTestTask;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.templateLanguages.TemplatesService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.util.Set;

/**
 * @author traff
 */
public abstract class DjangoManageTestTask extends PyExecutionFixtureTestTask {
  private StringBuilder myOutput;
  private StringBuilder myStdErr;
  private StringBuilder myStdOut;

  private RunContentDescriptor myDescriptor;
  private ProcessHandler myProcessHandler;
  private ExecutionConsole myConsoleView;
  private Sdk mySdk;

  @Override
  protected abstract String getTestDataPath();

  @Override
  public void setUp(String testName) throws Exception {
    super.setUp(testName);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        Module module = myFixture.getModule();
        if (module != null && !DjangoFacet.isPresent(module)) {
          DjangoTestCase.addDjangoFacet(module);
          TemplatesService.getInstance(module).setTemplateLanguage(TemplatesService.DJANGO);
        }
        TemplatesService.getInstance(module).setTemplateLanguage(TemplatesService.DJANGO);
      }
    });
  }

  @Override
  public void tearDown() throws Exception {
    TemplatesService.getInstance(myFixture.getModule()).setTemplateLanguage(TemplatesService.NONE);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        try {
          if (myConsoleView != null) {
            Disposer.dispose(myConsoleView);
            myConsoleView = null;
          }
          if (myDescriptor != null) {
            Disposer.dispose(myDescriptor);
            myDescriptor = null;
          }
          DjangoManageTestTask.super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private void createTempSdk(@NotNull final String sdkHome) {

    mySdk = PythonSdkType.findSdkByPath(sdkHome);

    if (mySdk == null) {
      final VirtualFile binary = LocalFileSystem.getInstance().findFileByPath(sdkHome);
      if (binary != null) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          public void run() {
            mySdk = SdkConfigurationUtil.createAndAddSDK(sdkHome, PythonSdkType.getInstance());
          }
        });
      }
    }
  }

  @Nullable
  public abstract ConfigurationFactory getFactory();

  @Override
  public void runTestOn(final String sdkHome) throws Exception {
    createTempSdk(sdkHome);
    before();

    final Semaphore s = new Semaphore();
    s.down();


    myOutput = new StringBuilder();
    myStdErr = new StringBuilder();
    myStdOut = new StringBuilder();

    runProcess(sdkHome, s);

    try {
      testing();
      Assert.assertTrue(s.waitFor(60000));
      XDebuggerTestUtil.waitForSwing();
      after();
    }
    finally {

      disposeProcess(myProcessHandler);
    }
  }

  private void runProcess(final String sdkHome, final Semaphore s) throws ExecutionException, IOException {
    final ConfigurationFactory factory = getFactory();

    if (factory == null) {     // PythonTask (there is no run configuration)
      final Module module = myFixture.getModule();
      final DjangoManageTask task = new DjangoManageTask(module, DjangoBundle.message("manage.run.tab.name"), mySdk);

      task.setWorkingDirectory(getWorkingFolder());
      task.setRunnerScript(PythonHelpersLocator.getHelperPath("pycharm/django_manage.py"));
      ImmutableList.Builder<String> parametersString =
        new ImmutableList.Builder<String>().add(getSubcommand()).add(getTestDataPath());

      task.setParameters(parametersString.build());

      myProcessHandler = task.createProcess(null);

      myProcessHandler.addProcessListener(createProcessListener());

      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          final Project project = myFixture.getProject();
          new RunContentExecutor(project, myProcessHandler)
            .withFilter(new PythonTracebackFilter(project))
            .run();
        }
      });
      s.up();
    }
    else {      //run dango server and django test
      final Project project = getProject();
      final RunnerAndConfigurationSettings settings =
        RunManager.getInstance(project).createRunConfiguration("test", factory);

      final AbstractPythonRunConfiguration config = (AbstractPythonRunConfiguration)settings.getConfiguration();
      config.addSourceRoots(DjangoTestsConfigurationType.getInstance().getDjangoTestsFactory().equals(factory));
      config.addContentRoots(DjangoTestsConfigurationType.getInstance().getDjangoTestsFactory().equals(factory));
      config.setSdkHome(sdkHome);
      config.setWorkingDirectory(getWorkingFolder());
      configure(config);

      new WriteAction() {
        @Override
        protected void run(Result result) throws Throwable {
          RunManagerEx.getInstanceEx(project).addConfiguration(settings, false);
          RunManagerEx.getInstanceEx(project).setSelectedConfiguration(settings);
          Assert.assertSame(settings, RunManagerEx.getInstanceEx(project).getSelectedConfiguration());
        }
      }.execute();

      final ProgramRunner runner = ProgramRunnerUtil.getRunner(DefaultRunExecutor.EXECUTOR_ID, settings);

      Assert.assertTrue(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, config));

      final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      final ExecutionEnvironment env = new ExecutionEnvironment(executor, runner, settings, project);

      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          try {
            runner.execute(env, new ProgramRunner.Callback() {
              @Override
              public void processStarted(RunContentDescriptor descriptor) {
                myDescriptor = descriptor;
                myProcessHandler = myDescriptor.getProcessHandler();
                myProcessHandler.addProcessListener(createProcessListener());
                myConsoleView = myDescriptor.getExecutionConsole();
                s.up();
              }
            });
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
  }

  private ProcessAdapter createProcessListener() {
    return new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        myOutput.append(event.getText());
        if (outputType == ProcessOutputTypes.STDOUT) {
          myStdOut.append(event.getText());
        }
        else if (outputType == ProcessOutputTypes.STDERR) {
          myStdErr.append(event.getText());
        }
      }
    };
  }

  protected abstract String getSubcommand();

  protected void configure(AbstractPythonRunConfiguration config) throws IOException {
  }

  @NotNull
  protected String output() {
    return myOutput.toString();
  }

  @NotNull
  protected String stderr() {
    return myStdErr.toString();
  }

  @NotNull
  protected String stdout() {
    return myStdOut.toString();
  }

  public void waitForOutput(@NotNull final String string) throws InterruptedException {
    int count = 0;
    while (!output().contains(string)) {
      if (count > 10) {
        Assert.fail("'" + string + "'" + " is not present in output.\n" + output());
      }
      Thread.sleep(2000);
      count++;
    }
  }

  @Override
  public Set<String> getTags() {
    return ImmutableSet.of("django");
  }
}
