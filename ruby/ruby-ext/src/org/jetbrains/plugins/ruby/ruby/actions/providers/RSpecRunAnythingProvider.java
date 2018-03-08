package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.rspec.run.RSpecRunConfiguration;
import org.jetbrains.plugins.ruby.testing.rspec.run.RSpecRunConfigurationType;

import java.util.List;
import java.util.Objects;

import static org.jetbrains.plugins.ruby.testing.rspec.run.RSpecRunCommandLineState.RSPEC_EXAMPLE_NAME_KEY;

public class RSpecRunAnythingProvider extends RubyRunAnythingProviderBase<RSpecRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "rspec";
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return RSpecRunConfigurationType.getInstance().getConfigurationFactories()[0];
  }

  @Override
  void extendConfiguration(@NotNull RSpecRunConfiguration configuration,
                           @NotNull VirtualFile baseDirectory,
                           @NotNull String commandLine) {

    List<String> options = ContainerUtil.newArrayList();
    boolean isExampleFilter = false;
    for (String argument : getArguments(commandLine)) {
      if (isExampleFilter) {
        configuration.setExampleNameFilter(argument);
        isExampleFilter = false;
        continue;
      }

      if (RSPEC_EXAMPLE_NAME_KEY.equals(argument)) {
        isExampleFilter = true;
        continue;
      }
      else if (argument.startsWith("-") && argument.length() > 1) {
        options.add(argument);
        continue;
      }

      configuration.setTestScriptPath(Objects.requireNonNull(findProgramFile(baseDirectory, argument)));
    }

    appendParameters(parameter -> configuration.setRunnerOptions(parameter), () -> configuration.getRunnerOptions(), options);
  }
}