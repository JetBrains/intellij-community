package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.console.config.IrbRunConfiguration;
import org.jetbrains.plugins.ruby.console.config.IrbRunConfigurationType;

import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;

public class IrbRunAnythingProvider extends RubyRunAnythingProviderBase<IrbRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "irb";
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return IrbRunConfigurationType.getInstance().getFactory();
  }

  @Override
  void extendConfiguration(@NotNull IrbRunConfiguration configuration,
                           @NotNull VirtualFile baseDirectory,
                           @NotNull String commandLine) {
    //hasn't implemented yet
  }
}