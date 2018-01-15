package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyRunConfigurationType;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfiguration;

import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;

public class RubyRunAnythingProvider extends RubyRunAnythingProviderBase<RubyRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "ruby";
  }

  @Override
  public boolean isMatched(@NotNull String commandLine) {
    return super.isMatched(commandLine) && getArguments(commandLine) != null;
  }

  @NotNull
  @Override
  public RubyRunConfigurationType.RubyRunConfigurationFactory getConfigurationFactory() {
    return RubyRunConfigurationType.getInstance().getRubyScriptFactory();
  }

  @Override
  void extendConfiguration(@NotNull RubyRunConfiguration configuration,
                           @NotNull VirtualFile baseDirectory,
                           @NotNull String commandLine) {
    configuration.setScriptPath(baseDirectory.getPath() + VFS_SEPARATOR_CHAR + getArguments(commandLine));
  }
}
