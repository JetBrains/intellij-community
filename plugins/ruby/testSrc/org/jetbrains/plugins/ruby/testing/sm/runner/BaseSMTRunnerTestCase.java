package org.jetbrains.plugins.ruby.testing.sm.runner;

import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfigurationFactory;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.SMTestRunnerResultsForm;
import org.jetbrains.plugins.ruby.testing.sm.runner.ui.TestResultsViewer;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestUnitRunConfiguration;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.TestUnitRunConfigurationType;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseSMTRunnerTestCase extends LightIdeaTestCase {
  protected SMTestProxy mySuite;
  protected SMTestProxy mySimpleTest;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySuite = createSuiteProxy();
    mySimpleTest = createTestProxy();
  }

  protected SMTestProxy createTestProxy() {
    return createTestProxy("test");
  }

  protected SMTestProxy createTestProxy(final SMTestProxy parentSuite) {
    return createTestProxy("test", parentSuite);
  }

  protected SMTestProxy createTestProxy(final String name) {
    return createTestProxy(name, null);
  }

  protected SMTestProxy createTestProxy(final String name, final SMTestProxy parentSuite) {
    final SMTestProxy proxy = new SMTestProxy(name, false);
    if (parentSuite != null) {
      parentSuite.addChild(proxy);
    }
    return proxy;
  }

  protected SMTestProxy createSuiteProxy(final String name) {
    return createSuiteProxy(name, null);
  }

  protected SMTestProxy createSuiteProxy(final String name, final SMTestProxy parentSuite) {
    final SMTestProxy suite = new SMTestProxy(name, true);
    if (parentSuite != null) {
      parentSuite.addChild(suite);
    }
    return suite;
  }

  protected SMTestProxy createSuiteProxy() {
    return createSuiteProxy("suite");
  }

  protected SMTestProxy createSuiteProxy(final SMTestProxy parentSuite) {
    return createSuiteProxy("suite", parentSuite);
  }

  protected RuntimeConfiguration createRunConfiguration() {
    final RubyRunConfigurationFactory factory = new RubyRunConfigurationFactory(
        TestUnitRunConfigurationType.getInstance());
    return new RTestUnitRunConfiguration(getProject(), factory, "name");
  }

  protected TestConsoleProperties createConsoleProperties() {
    final RuntimeConfiguration runConfiguration = createRunConfiguration();

    final TestConsoleProperties consoleProperties = new SMTRunnerConsoleProperties(runConfiguration);
    TestConsoleProperties.HIDE_PASSED_TESTS.set(consoleProperties, false);
    
    return consoleProperties;
  }

  protected TestResultsViewer createResultsViewer(final TestConsoleProperties consoleProperties) {
    final ExecutionEnvironment environment = new ExecutionEnvironment();
    return new SMTestRunnerResultsForm(consoleProperties.getConfiguration(),
                                    consoleProperties,
                                    environment.getRunnerSettings(),
                                    environment.getConfigurationSettings());
  }

  protected void doPassTest(final SMTestProxy test) {
    test.setStarted();
    test.setFinished();
  }

  protected void doFailTest(final SMTestProxy test) {
    test.setStarted();
    test.setTestFailed("", "", false);
    test.setFinished();
  }

  protected void doErrorTest(final SMTestProxy test) {
    test.setStarted();
    test.setTestFailed("", "", true);
    test.setFinished();
  }
}
