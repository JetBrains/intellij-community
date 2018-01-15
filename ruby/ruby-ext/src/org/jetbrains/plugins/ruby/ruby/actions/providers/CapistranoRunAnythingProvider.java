package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.tasks.capistrano.run.configuration.CapistranoRunConfiguration;
import org.jetbrains.plugins.ruby.tasks.capistrano.run.configuration.CapistranoRunConfigurationType;

public class CapistranoRunAnythingProvider extends RubyRunAnythingProviderBase<CapistranoRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "cap";
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return CapistranoRunConfigurationType.getInstance().getFactory();
  }

  @Override
  void extendConfiguration(@NotNull CapistranoRunConfiguration configuration,
                           @NotNull VirtualFile baseDirectory,
                           @NotNull String commandLine) {
    configuration.setTaskName(getArguments(commandLine));
  }
}