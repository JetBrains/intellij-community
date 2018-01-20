package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.tasks.capistrano.run.configuration.CapistranoRunConfiguration;
import org.jetbrains.plugins.ruby.tasks.capistrano.run.configuration.CapistranoRunConfigurationType;

import java.util.List;

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
    List<String> options = ContainerUtil.newArrayList();
    boolean isStage = true;
    for (String argument : StringUtil.split(getArguments(commandLine), " ")) {
      if (isStage) {
        configuration.setStage(argument);
        isStage = false;
        continue;
      }

      if (argument.startsWith("-") && argument.length() > 1 || argument.startsWith("--") && argument.length() > 2) {
        options.add(argument);
        continue;
      }

      if (!StringUtil.startsWith(argument, "-")) {
        configuration.setTaskName(argument);
      }
    }

    appendParameters(parameter -> configuration.setTaskArgs(parameter), () -> configuration.getTaskArgs(), options);
  }
}