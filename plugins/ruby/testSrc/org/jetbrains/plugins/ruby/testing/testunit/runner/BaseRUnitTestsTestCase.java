package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfigurationFactory;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;
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

  protected RTestUnitTestProxy createTestProxy(final String name) {
    return new RTestUnitTestProxy(name, false);
  }

  protected RTestUnitTestProxy createSuiteProxy(final String name) {
    return new RTestUnitTestProxy(name, true);
  }

  protected RTestUnitTestProxy createSuiteProxy() {
    return createSuiteProxy("suite");
  }

  protected RTestsRunConfiguration createRTestsRunConfiguration() {
    final RubyRunConfigurationFactory factory = new RubyRunConfigurationFactory(
        TestUnitRunConfigurationType.getInstance());
    return new RTestsRunConfiguration(getProject(), factory, "name");
  }

  protected RTestUnitConsoleProperties createConsoleProperties() {
    final RTestsRunConfiguration runConfiguration = createRTestsRunConfiguration();

    final RTestUnitConsoleProperties consoleProperties = new RTestUnitConsoleProperties(runConfiguration);
    TestConsoleProperties.HIDE_PASSED_TESTS.set(consoleProperties, false);
    
    return consoleProperties;
  }

  protected TestResultsViewer createResultsViewer(final RTestUnitConsoleProperties consoleProperties) {
    final ExecutionEnvironment environment = new ExecutionEnvironment();
    final TestResultsViewer resultsViewer =
        new RTestUnitResultsForm(consoleProperties.getConfiguration(),
                                 consoleProperties,
                                 environment.getRunnerSettings(),
                                 environment.getConfigurationSettings());
    return resultsViewer;
  }
}
