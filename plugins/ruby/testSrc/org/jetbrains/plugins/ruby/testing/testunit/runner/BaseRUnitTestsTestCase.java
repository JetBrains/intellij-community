package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.testFramework.LightIdeaTestCase;
import org.jetbrains.plugins.ruby.testing.testunit.runner.model.RTestUnitTestProxy;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.TestUnitRunConfigurationType;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfigurationFactory;

/**
 * @author Roman Chernyatchik
 */
public abstract class BaseRUnitTestsTestCase extends LightIdeaTestCase {
  protected RTestUnitTestProxy myRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRoot = createTestProxy();
  }

  protected RTestUnitTestProxy createTestProxy() {
    return createTestProxy("");
  }

  protected RTestUnitTestProxy createTestProxy(final String name) {
    return new RTestUnitTestProxy(name);
  }

  protected RTestsRunConfiguration createRTestsRunConfiguration() {
    final RubyRunConfigurationFactory factory = new RubyRunConfigurationFactory(
        TestUnitRunConfigurationType.getInstance());
    final RTestsRunConfiguration runConfiguration = new RTestsRunConfiguration(getProject(), factory, "name");
    return runConfiguration;
  }
}
