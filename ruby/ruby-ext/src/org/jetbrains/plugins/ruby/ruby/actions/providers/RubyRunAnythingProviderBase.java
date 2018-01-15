package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingProvider;
import org.jetbrains.plugins.ruby.ruby.run.configuration.AbstractRubyRunConfiguration;

public abstract class RubyRunAnythingProviderBase<T extends AbstractRubyRunConfiguration> extends RunAnythingProvider {
  @Override
  public boolean isMatched(@NotNull String commandLine) {
    return commandLine.startsWith(getExecCommand());
  }

  @NotNull
  abstract String getExecCommand();

  @Nullable
  String getArguments(@NotNull String commandLine) {
    return StringUtil.substringAfter(commandLine, getExecCommand() + " ");
  }

  void extendConfiguration(@NotNull T configuration, @NotNull VirtualFile baseDirectory, @NotNull String commandLine) { }

  @Override
  public RunnerAndConfigurationSettings createConfiguration(@NotNull Project project,
                                                            @NotNull String commandLine,
                                                            @Nullable VirtualFile workingDirectory) {
    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(project).createRunConfiguration(commandLine, getConfigurationFactory());

    final AbstractRubyRunConfiguration templateConfiguration = (AbstractRubyRunConfiguration)settings.getConfiguration();

    templateConfiguration.setWorkingDirectory(workingDirectory == null ? null : workingDirectory.getPath());

    VirtualFile baseDir = workingDirectory == null ? project.getBaseDir() : workingDirectory;
    //noinspection unchecked
    extendConfiguration((T)templateConfiguration, baseDir, commandLine);

    return settings;
  }
}
