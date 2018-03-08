package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingProvider;
import org.jetbrains.plugins.ruby.ruby.run.configuration.AbstractRubyRunConfiguration;

import java.io.IOException;
import java.util.List;

public abstract class RubyRunAnythingProviderBase<T extends AbstractRubyRunConfiguration> extends RunAnythingProvider {
  private static final Logger LOG = Logger.getInstance(RubyRunAnythingProviderBase.class);
  private static final String DEFAULT_EMPTY_PATH = "";

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
  String[] getArguments(@NotNull String commandLine) {
    String arguments = StringUtil.substringAfter(commandLine, getExecCommand() + " ");
    if (arguments == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    return ParametersList.parse(arguments);
  }

  void extendConfiguration(@NotNull T configuration, @NotNull VirtualFile baseDirectory, @NotNull String commandLine) { }

  @NotNull
  String findProgramFile(@NotNull VirtualFile baseDirectory, @NotNull String fileName) {
    VirtualFile file = VfsUtilCore.findRelativeFile(fileName, baseDirectory);

    if (file != null) {
      return ObjectUtils.notNull(file.getCanonicalPath(), DEFAULT_EMPTY_PATH);
    }

    try {
      file = ScratchFileService.getInstance().findFile(ScratchRootType.getInstance(), fileName, ScratchFileService.Option.existing_only);
      return file == null ? DEFAULT_EMPTY_PATH : ObjectUtils.notNull(file.getCanonicalPath(), DEFAULT_EMPTY_PATH);
    }
    catch (IOException e) {
      LOG.warn(RBundle.message("run.anything.run.configuration.provider.warning", fileName));
    }

    return DEFAULT_EMPTY_PATH;
  }

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

  protected static void appendParameters(@NotNull Consumer<String> set, @NotNull Computable<String> predefined,
                                         @NotNull List<String> parameters) {
    String defaults = predefined.compute();
    set.consume((StringUtil.isEmpty(defaults) ? "" : defaults + " ") + StringUtil.join(parameters, " "));
  }
}
