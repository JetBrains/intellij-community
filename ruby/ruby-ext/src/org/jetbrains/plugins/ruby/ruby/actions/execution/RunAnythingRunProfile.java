package org.jetbrains.plugins.ruby.ruby.actions.execution;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.jetbrains.plugins.ruby.ruby.actions.RunAnythingCommandItem.UNDEFINED_COMMAND_ICON;

public class RunAnythingRunProfile implements RunProfile {
  @NotNull private final String myOriginalCommand;
  @NotNull private final GeneralCommandLine myCommandLine;

  public RunAnythingRunProfile(@NotNull GeneralCommandLine commandLine,
                               @NotNull String originalCommand) {
    myCommandLine = commandLine;
    myOriginalCommand = originalCommand;
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
    return new RunAnythingRunProfileState(environment, myOriginalCommand);
  }

  @Override
  public String getName() {
    return myOriginalCommand;
  }

  @NotNull
  public String getOriginalCommand() {
    return myOriginalCommand;
  }

  @NotNull
  public GeneralCommandLine getCommandLine() {
    return myCommandLine;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return UNDEFINED_COMMAND_ICON;
  }

}
