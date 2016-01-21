package com.jetbrains.env;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.actions.RerunFailedActionsTestTools;
import com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;

/**
 * Runner for tests. Provides access to test console and test results.
 * <p/>
 * It supports <strong>failed test rerun</strong>. You only need to provide number of times to rerun as ctor param
 * and then use {@link #getCurrentRerunStep()} to find number of rerun
 *
 * @author Ilya.Kazakevich
 */
public class PyAbstractTestProcessRunner<CONF_T extends AbstractPythonRunConfigurationParams>
  extends ConfigurationBasedProcessRunner<CONF_T> {

  private final int myTimesToRerunFailedTests;

  private int myCurrentRerunStep;


  private SMTRunnerConsoleView myExecutionConsole;
  private SMRootTestsCounter myProxyManager;


  /**
   * @param timesToRerunFailedTests how many times rerun failed tests (0 not to rerun at all)
   * @see ConfigurationBasedProcessRunner#ConfigurationBasedProcessRunner(ConfigurationFactory, Class, String)
   */
  public PyAbstractTestProcessRunner(@NotNull final ConfigurationFactory configurationFactory,
                                     @NotNull final Class<CONF_T> expectedConfigurationType,
                                     @Nullable final String workingFolder,
                                     final int timesToRerunFailedTests) {
    super(configurationFactory, expectedConfigurationType, workingFolder);
    myTimesToRerunFailedTests = timesToRerunFailedTests;
  }


  @Override
  protected void fetchConsoleAndSetToField(@NotNull final RunContentDescriptor descriptor) {
    // Fetch test results from console
    myExecutionConsole = (SMTRunnerConsoleView)descriptor.getExecutionConsole();
    final JComponent component = myExecutionConsole.getComponent();
    assert component != null;
    myConsole = (ConsoleViewImpl)myExecutionConsole.getConsole();
    myProxyManager = new SMRootTestsCounter(myExecutionConsole.getResultsViewer().getTestsRootNode());
  }

  @Override
  protected void prepareConsoleAfterProcessEnd() {
    super.prepareConsoleAfterProcessEnd();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        // Print output of tests to console (because console may be scrolled)
        myProxyManager.getProxy().getAllTests().get(0).printOn(myExecutionConsole.getPrinter());
      }
    }, ModalityState.NON_MODAL);
  }

  /**
   * Ensures all test passed
   */
  public void assertAllTestsPassed() {
    final String consoleText = getAllConsoleText();
    Assert.assertEquals(consoleText, 0, myProxyManager.getProxy().getChildren(Filter.NOT_PASSED).size());
    Assert.assertEquals(consoleText, 0, getFailedTestsCount());
  }

  /**
   * Searches for test by its name recursevly in {@link #myTestProxy}
   *
   * @param testName test name to find
   * @return test
   * @throws AssertionError if no test found
   */
  @NotNull
  public AbstractTestProxy findTestByName(@NotNull final String testName) {
    final AbstractTestProxy test = findTestByName(testName, myProxyManager.getProxy());
    assert test != null : "No test found with name" + testName;
    return test;
  }

  /**
   * @return test results proxy
   */
  @NotNull
  public SMRootTestProxy getTestProxy() {
    return myProxyManager.getProxy();
  }


  /**
   * Searches for test by its name recursevly in test, passed as arumuent.
   *
   * @param testName test name to find
   * @param test     root test
   * @return test or null if not found
   */
  @Nullable
  private static AbstractTestProxy findTestByName(@NotNull final String testName, @NotNull final AbstractTestProxy test) {
    if (test.getName().equals(testName)) {
      return test;
    }
    for (final AbstractTestProxy testProxy : test.getChildren()) {
      final AbstractTestProxy result = findTestByName(testName, testProxy);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * @return number of failed tests
   */
  public int getFailedTestsCount() {
    return myProxyManager.getFailedTestsCount();
  }

  /**
   * @return number of passed tests
   */
  public int getPassedTestsCount() {
    return myProxyManager.getPassedTestsCount();
  }

  /**
   * @return number of all tests
   */
  public int getAllTestsCount() {
    return myProxyManager.getAllTestsCount();
  }


  @Nullable
  @Override
  protected ExecutionEnvironment getEnvironmentToRerun(@NotNull final RunContentDescriptor lastRunDescriptor) {
    if (myTimesToRerunFailedTests == myCurrentRerunStep) {
      return null;
    }

    final Logger logger = Logger.getInstance(PyAbstractTestProcessRunner.class);
    if (getFailedTestsCount() == 0) {
      logger
        .warn(String.format("No failed tests on iteration %d, not sure what to rerun", myCurrentRerunStep));
    }
    logger.info(String.format("Starting iteration %s", myCurrentRerunStep));

    myCurrentRerunStep++;

    return RerunFailedActionsTestTools.findRestartAction(lastRunDescriptor);
  }

  /**
   * @return number of rerun launch or 0 if first run
   */
  public int getCurrentRerunStep() {
    return myCurrentRerunStep;
  }
}
