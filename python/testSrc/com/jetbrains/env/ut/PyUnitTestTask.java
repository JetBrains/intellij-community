package com.jetbrains.env.ut;

import com.google.common.collect.Lists;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

/**
 * Tasks to run unit test configurations.
 * You should extend it either implementing {@link #after()} and {@link #before()} or implement {@link #runTestOn(String)}
 * yourself and use {@link #runConfiguration(com.intellij.execution.configurations.ConfigurationFactory, String, com.intellij.openapi.project.Project)}
 * or {@link #runConfiguration(com.intellij.execution.RunnerAndConfigurationSettings, com.intellij.execution.configurations.RunConfiguration)} .
 * Use {@link #myDescriptor} and {@link #myConsoleView} to check output
 *
 * @author traff
 */
public abstract class PyUnitTestTask extends PyExecutionFixtureTestTask {

  protected ProcessHandler myProcessHandler;
  private boolean shouldPrintOutput = false;
  /**
   * Test root node
   */
  protected SMTestProxy.SMRootTestProxy myTestProxy;
  /**
   * Output test console
   */
  protected SMTRunnerConsoleView myConsoleView;
  /**
   * Test run descriptor
   */
  protected RunContentDescriptor myDescriptor;

  private StringBuilder myOutput;
  private boolean mySetUp = false;

  public PyUnitTestTask() {
  }

  public PyUnitTestTask(String workingFolder, String scriptName, String scriptParameters) {
    setWorkingFolder(getTestDataPath() + workingFolder);
    setScriptName(scriptName);
    setScriptParameters(scriptParameters);
  }

  public PyUnitTestTask(String workingFolder, String scriptName) {
    this(workingFolder, scriptName, null);
  }

  @Override
  public void setUp(final String testName) throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          if (myFixture == null) {
            PyUnitTestTask.super.setUp(testName);
            mySetUp = true;
          }
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @NotNull
  protected String output() {
    return myOutput.toString();
  }

  @Override
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
                                   @Override
                                   public void run() {
                                     try {
                                       if (mySetUp) {
                                         if (myConsoleView != null) {
                                           Disposer.dispose(myConsoleView);
                                           myConsoleView = null;
                                         }
                                         if (myDescriptor != null) {
                                           Disposer.dispose(myDescriptor);
                                           myDescriptor = null;
                                         }


                                         PyUnitTestTask.super.tearDown();

                                         mySetUp = false;
                                       }
                                     }
                                     catch (Exception e) {
                                       throw new RuntimeException(e);
                                     }
                                   }
                                 }

    );
  }

  @Override
  public void runTestOn(String sdkHome) throws Exception {
    final Project project = getProject();
    final ConfigurationFactory factory = PythonTestConfigurationType.getInstance().PY_UNITTEST_FACTORY;
    runConfiguration(factory, sdkHome, project);
  }

  protected void runConfiguration(ConfigurationFactory factory, String sdkHome, final Project project) throws Exception {
    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration("test", factory);

    AbstractPythonTestRunConfiguration config = (AbstractPythonTestRunConfiguration)settings.getConfiguration();


    config.setSdkHome(sdkHome);
    config.setScriptName(getScriptPath());
    config.setWorkingDirectory(getWorkingFolder());

    PythonSdkFlavor sdk = PythonSdkFlavor.getFlavor(sdkHome);


    if (sdk instanceof JythonSdkFlavor) {
      config.setInterpreterOptions(JythonSdkFlavor.getPythonPathCmdLineArgument(Lists.<String>newArrayList(getWorkingFolder())));
    }
    else {
      PythonEnvUtil.addToPythonPath(config.getEnvs(), getWorkingFolder());
    }


    configure(config);

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        RunManagerEx.getInstanceEx(project).addConfiguration(settings, false);
        RunManagerEx.getInstanceEx(project).setSelectedConfiguration(settings);
        Assert.assertSame(settings, RunManagerEx.getInstanceEx(project).getSelectedConfiguration());
      }
    }.execute();

    runConfiguration(settings, config);
  }

  /**
   * Run configuration.
   *
   * @param settings settings (if have any, null otherwise)
   * @param config configuration to run
   * @throws Exception
   */
  protected void runConfiguration(@Nullable final RunnerAndConfigurationSettings settings,
                                  @NotNull final RunConfiguration config) throws Exception {
    final ExecutionEnvironment environment;
    if (settings == null) {
      environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), config).build();
    }
    else {
      environment = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), settings).build();
    }
    //noinspection ConstantConditions

    Assert.assertTrue(environment.getRunner().canRun(DefaultRunExecutor.EXECUTOR_ID, config));


    before();

    final com.intellij.util.concurrency.Semaphore s = new com.intellij.util.concurrency.Semaphore();
    s.down();

    myOutput = new StringBuilder();

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          environment.getRunner().execute(environment, new ProgramRunner.Callback() {
            @Override
            public void processStarted(RunContentDescriptor descriptor) {
              myDescriptor = descriptor;
              myProcessHandler = myDescriptor.getProcessHandler();
              myProcessHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void onTextAvailable(ProcessEvent event, Key outputType) {
                  myOutput.append(event.getText());
                }
              });
              myConsoleView = (SMTRunnerConsoleView)descriptor.getExecutionConsole();
              myTestProxy = myConsoleView.getResultsViewer().getTestsRootNode();
              myConsoleView.getResultsViewer().addEventsListener(new TestResultsViewer.SMEventsAdapter() {
                @Override
                public void onTestingFinished(TestResultsViewer sender) {
                  s.up();
                }
              });
            }
          });
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    Assert.assertTrue(s.waitFor(getTestTimeout()));

    XDebuggerTestUtil.waitForSwing();

    assertFinished();

    Assert.assertTrue(output(), allTestsCount() > 0);

    after();

    disposeProcess(myProcessHandler);
  }

  protected int getTestTimeout() {
    return 60000;
  }

  protected void configure(AbstractPythonTestRunConfiguration config) {
  }

  public void allTestsPassed() {
    Assert.assertEquals(output(), 0, myTestProxy.getChildren(Filter.NOT_PASSED).size());
    Assert.assertEquals(output(), 0, failedTestsCount());
  }

  public int failedTestsCount() {
    return myTestProxy.collectChildren(NOT_SUIT.and(Filter.FAILED_OR_INTERRUPTED)).size();
  }

  public int passedTestsCount() {
    return myTestProxy.collectChildren(NOT_SUIT.and(Filter.PASSED)).size();
  }

  public void assertFinished() {
    Assert.assertTrue("State is " + myTestProxy.getMagnitudeInfo().getTitle() + "\n" + output(),
                      myTestProxy.wasLaunched() && !myTestProxy.wasTerminated());
  }

  public int allTestsCount() {
    return myTestProxy.collectChildren(NOT_SUIT).size();
  }

  public static final Filter<SMTestProxy> NOT_SUIT = new Filter<SMTestProxy>() {
    @Override
    public boolean shouldAccept(SMTestProxy test) {
      return !test.isSuite();
    }
  };
}
