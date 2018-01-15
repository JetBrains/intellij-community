package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.rails.run.configuration.server.RailsServerRunConfiguration;
import org.jetbrains.plugins.ruby.rails.run.configuration.server.RailsServerRunConfigurationType;

public class RailsServerRunAnythingProvider extends RubyRunAnythingProviderBase<RailsServerRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "rails server";
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return RailsServerRunConfigurationType.getInstance().getConfigurationFactories()[0];
  }
}
