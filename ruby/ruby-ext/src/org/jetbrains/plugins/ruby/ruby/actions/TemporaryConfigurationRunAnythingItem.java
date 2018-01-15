package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.run.configuration.AbstractRubyRunConfiguration;

import javax.swing.*;

public class TemporaryConfigurationRunAnythingItem extends RunAnythingItem {
  @NotNull private final Project myProject;
  @NotNull private final String myCommandLineString;
  @NotNull private final RunnerAndConfigurationSettings mySettings;

  public TemporaryConfigurationRunAnythingItem(@NotNull Project project,
                                              @NotNull String commandLineString,
                                              @NotNull RunnerAndConfigurationSettings settings) {
    myProject = project;
    myCommandLineString = commandLineString;
    mySettings = settings;
  }

  @Override
  public void run(@NotNull Executor executor, @Nullable VirtualFile workDirectory) {
    RunManagerEx.getInstanceEx(myProject).setTemporaryConfiguration(mySettings);
    RunManager.getInstance(myProject).setSelectedConfiguration(mySettings);
    RunConfiguration configuration = mySettings.getConfiguration();

    if (configuration instanceof AbstractRubyRunConfiguration) {
      ((AbstractRubyRunConfiguration)configuration)
        .setWorkingDirectory(RunAnythingItem.getActualWorkDirectory(myProject, workDirectory));
    }

    ExecutionUtil.runConfiguration(mySettings, executor);
  }

  @Override
  public String getText() {
    return myCommandLineString;
  }

  @Override
  public Icon getIcon() {
    return mySettings.getFactory().getIcon();
  }
}