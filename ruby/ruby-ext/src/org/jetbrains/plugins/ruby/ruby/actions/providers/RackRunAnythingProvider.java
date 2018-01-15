package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.rack.run.RackRunConfiguration;
import org.jetbrains.plugins.ruby.rack.run.RackRunConfigurationType;

public class RackRunAnythingProvider extends RubyRunAnythingProviderBase<RackRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "rackup";
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return RackRunConfigurationType.getInstance().getFactory();
  }
}