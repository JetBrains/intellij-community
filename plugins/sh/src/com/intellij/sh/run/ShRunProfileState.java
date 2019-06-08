// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.ShStringUtil.quote;

public class ShRunProfileState implements RunProfileState {
  private static final String WHITESPACE = " ";
  private final Project myProject;
  private final ShRunConfiguration myRunConfiguration;

  public ShRunProfileState(@NotNull Project project, @NotNull ShRunConfiguration runConfiguration) {
    myProject = project;
    myRunConfiguration = runConfiguration;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ShRunner shRunner = ServiceManager.getService(myProject, ShRunner.class);
    if (shRunner == null || !shRunner.isAvailable(myProject) || !(shRunner instanceof ShTerminalRunner)) {
      GeneralCommandLine commandLine = createCommandLine();
      return ShFailoverRunnerUtil.buildExecutionResult(myProject, commandLine);
    }
    ((ShTerminalRunner)shRunner).run(buildCommand());
    return null;
  }

  @NotNull
  private GeneralCommandLine createCommandLine() throws ExecutionException {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(myRunConfiguration.getScriptPath());
    if (virtualFile == null || virtualFile.getParent() == null) {
      throw new ExecutionException("Cannot determine shell script parent directory");
    }

    PtyCommandLine commandLine = new PtyCommandLine();
    if (!SystemInfoRt.isWindows) {
      commandLine.getEnvironment().put("TERM", "xterm-256color");
    }
    commandLine.withConsoleMode(false);
    commandLine.withInitialColumns(120);
    commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    commandLine.setWorkDirectory(virtualFile.getParent().getPath());

    commandLine.setExePath(myRunConfiguration.getInterpreterPath());
    if (StringUtil.isNotEmpty(myRunConfiguration.getInterpreterOptions())) {
      commandLine.addParameter(myRunConfiguration.getInterpreterOptions());
    }
    commandLine.addParameter(myRunConfiguration.getScriptPath());
    if (StringUtil.isNotEmpty(myRunConfiguration.getScriptOptions())) {
      commandLine.addParameter(myRunConfiguration.getScriptOptions());
    }
    return commandLine;
  }

  @NotNull
  private String buildCommand() {
    return quote(myRunConfiguration.getInterpreterPath()) +
           WHITESPACE +
           myRunConfiguration.getInterpreterOptions() +
           WHITESPACE +
           quote(myRunConfiguration.getScriptPath()) +
           WHITESPACE +
           myRunConfiguration.getScriptOptions() +
           "\n";
  }
}
