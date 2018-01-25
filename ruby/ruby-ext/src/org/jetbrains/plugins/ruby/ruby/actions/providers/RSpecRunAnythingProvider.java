package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.rspec.run.RSpecRunConfiguration;
import org.jetbrains.plugins.ruby.testing.rspec.run.RSpecRunConfigurationType;

import java.io.File;
import java.util.List;

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
    for (String argument : getArguments(commandLine)) {
      if (RSPEC_EXAMPLE_NAME_KEY.equals(argument)) {
        configuration.setExampleNameFilter(argument);
      }
      else if (argument.startsWith("-") && argument.length() > 1) {
        options.add(argument);
        continue;
      }

      configuration.setTestScriptPath(new File(baseDirectory.getPath(), argument).getAbsolutePath());
    }

    appendParameters(parameter -> configuration.setRunnerOptions(parameter), () -> configuration.getRunnerOptions(), options);
  }
}