package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfigurationFactory;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestUnitRunConfiguration;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.TestUnitRunConfigurationType;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.TestResultsViewer;
import org.jetbrains.plugins.ruby.testing.testunit.runner.ui.RTestUnitResultsForm;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseRUnitTestsTestCase extends LightIdeaTestCase {
  protected RTestUnitTestProxy mySuite;
  protected RTestUnitTestProxy mySimpleTest;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySuite = createSuiteProxy();
    mySimpleTest = createTestProxy();
  }

  protected RTestUnitTestProxy createTestProxy() {
    return createTestProxy("test");
  }

  protected RTestUnitTestProxy createTestProxy(final RTestUnitTestProxy parentSuite) {
    return createTestProxy("test", parentSuite);
  }

  protected RTestUnitTestProxy createTestProxy(final String name) {
    return createTestProxy(name, null);
  }

  protected RTestUnitTestProxy createTestProxy(final String name, final RTestUnitTestProxy parentSuite) {
    final RTestUnitTestProxy proxy = new RTestUnitTestProxy(name, false);
    if (parentSuite != null) {
      parentSuite.addChild(proxy);
    }
    return proxy;
  }

  protected RTestUnitTestProxy createSuiteProxy(final String name) {
    return createSuiteProxy(name, null);
  }

  protected RTestUnitTestProxy createSuiteProxy(final String name, final RTestUnitTestProxy parentSuite) {
    final RTestUnitTestProxy suite = new RTestUnitTestProxy(name, true);
    if (parentSuite != null) {
      parentSuite.addChild(suite);
    }
    return suite;
  }

  protected RTestUnitTestProxy createSuiteProxy() {
    return createSuiteProxy("suite");
  }

  protected RTestUnitTestProxy createSuiteProxy(final RTestUnitTestProxy parentSuite) {
    return createSuiteProxy("suite", parentSuite);
  }

  protected RTestUnitRunConfiguration createRTestsRunConfiguration() {
    final RubyRunConfigurationFactory factory = new RubyRunConfigurationFactory(
        TestUnitRunConfigurationType.getInstance());
    return new RTestUnitRunConfiguration(getProject(), factory, "name");
  }

  protected RTestUnitConsoleProperties createConsoleProperties() {
    final RTestUnitRunConfiguration runConfiguration = createRTestsRunConfiguration();

    final RTestUnitConsoleProperties consoleProperties = new RTestUnitConsoleProperties(runConfiguration);
    TestConsoleProperties.HIDE_PASSED_TESTS.set(consoleProperties, false);
    
    return consoleProperties;
  }

  protected TestResultsViewer createResultsViewer(final RTestUnitConsoleProperties consoleProperties) {
    final ExecutionEnvironment environment = new ExecutionEnvironment();
    return new RTestUnitResultsForm(consoleProperties.getConfiguration(),
                                    consoleProperties,
                                    environment.getRunnerSettings(),
                                    environment.getConfigurationSettings());
  }

  protected void doPassTest(final RTestUnitTestProxy test) {
    test.setStarted();
    test.setFinished();
  }

  protected void doFailTest(final RTestUnitTestProxy test) {
    test.setStarted();
    test.setTestFailed("", "", false);
    test.setFinished();
  }

  protected void doErrorTest(final RTestUnitTestProxy test) {
    test.setStarted();
    test.setTestFailed("", "", true);
    test.setFinished();
  }
}
