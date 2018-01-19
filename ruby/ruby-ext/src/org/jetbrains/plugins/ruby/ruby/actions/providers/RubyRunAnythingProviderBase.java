package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingProvider;
import org.jetbrains.plugins.ruby.ruby.run.configuration.AbstractRubyRunConfiguration;

public abstract class RubyRunAnythingProviderBase<T extends AbstractRubyRunConfiguration> extends RunAnythingProvider {
  @Override
  public boolean isMatched(@NotNull Project project, @NotNull String commandLine, @Nullable VirtualFile workDirectory) {
    if (!commandLine.startsWith(getExecCommand())) return false;

    RunnerAndConfigurationSettings configuration = createConfiguration(project, commandLine, workDirectory);
    try {
      configuration.checkSettings();
    }
    catch (RuntimeConfigurationException e) {
      return false;
    }

    return true;
  }

  @NotNull
  abstract String getExecCommand();

  @NotNull
  String getArguments(@NotNull String commandLine) {
    String arguments = StringUtil.substringAfter(commandLine, getExecCommand() + " ");
    return StringUtil.notNullize(arguments);
  }

  void extendConfiguration(@NotNull T configuration, @NotNull VirtualFile baseDirectory, @NotNull String commandLine) { }

  @NotNull
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
