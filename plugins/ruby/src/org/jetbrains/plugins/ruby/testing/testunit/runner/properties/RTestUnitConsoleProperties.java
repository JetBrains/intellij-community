package org.jetbrains.plugins.ruby.testing.testunit.runner.properties;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitConsoleProperties extends TestConsoleProperties {
  @NonNls private static final String PREFIX = "RubyTestUnitSupport.";
  private final RTestsRunConfiguration myConfiguration;

  public RTestUnitConsoleProperties(final RTestsRunConfiguration config)
  {
    super(new Storage.PropertiesComponentStorage(PREFIX, PropertiesComponent.getInstance()), config.getProject());
    myConfiguration = config;
  }

  public boolean isDebug() {
    //TODO[romeo] implement
    return false;
  }

  public boolean isPaused() {
    //TODO[romeo] implement
    return false;
  }

  public RTestsRunConfiguration getConfiguration() {
    return myConfiguration;
  }
}
