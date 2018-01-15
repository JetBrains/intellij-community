package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.rspec.run.RSpecRunConfiguration;
import org.jetbrains.plugins.ruby.testing.rspec.run.RSpecRunConfigurationType;

import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;

public class RSpecRunAnythingProvider extends RubyRunAnythingProviderBase<RSpecRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "rspec";
  }

  @Override
  public boolean isMatched(@NotNull String commandLine) {
    return super.isMatched(commandLine) && getArguments(commandLine) != null;
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
    configuration.setTestScriptPath(baseDirectory.getPath() + VFS_SEPARATOR_CHAR + getArguments(commandLine));
  }
}