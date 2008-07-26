package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.testFramework.LightIdeaTestCase;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfigurationFactory;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.TestUnitRunConfigurationType;

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

    return new RTestUnitConsoleProperties(runConfiguration);
  }
}
