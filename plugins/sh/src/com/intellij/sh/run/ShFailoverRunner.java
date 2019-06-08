// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.sh.psi.ShFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import icons.SHIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ShFailoverRunner extends ShRunner {
  protected ShFailoverRunner(@NotNull Project project) {
    super(project);
  }

  @Override
  public void run(@NotNull ShFile file) {
    ExecutionEnvironmentBuilder builder = new ExecutionEnvironmentBuilder(myProject, DefaultRunExecutor.getRunExecutorInstance())
        .runProfile(new RunProfile() {
          @Override
          public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
            return new MyRunProfileState(file.getProject(), file.getVirtualFile());
          }

          @NotNull
          @Override
          public String getName() {
            return file.getName();
          }

          @Override
          public Icon getIcon() {
            return SHIcons.ShFile;
          }
        });
    ExecutionManager.getInstance(myProject).restartRunProfile(builder.build());
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    return true;
  }

  private static class MyRunProfileState implements RunProfileState {
    private final Project myProject;
    private final VirtualFile myShellScriptFile;

    private MyRunProfileState(@NotNull Project project,
                              @NotNull VirtualFile shellScriptFile) {
      myProject = project;
      myShellScriptFile = shellScriptFile;
    }

    @Nullable
    @Override
    public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
      VirtualFile dir = myShellScriptFile.getParent();
      if (dir == null) {
        throw new ExecutionException("Cannot determine shell script parent directory");
      }
      GeneralCommandLine commandLine = createCommandLine(dir);
      return ShFailoverRunnerUtil.buildExecutionResult(myProject, commandLine);
    }

    @NotNull
    private GeneralCommandLine createCommandLine(@NotNull VirtualFile workingDir) throws ExecutionException {
      PtyCommandLine commandLine = new PtyCommandLine();
      if (!SystemInfoRt.isWindows) {
        commandLine.getEnvironment().put("TERM", "xterm-256color");
      }
      commandLine.withConsoleMode(false);
      commandLine.withInitialColumns(120);
      commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
      commandLine.setWorkDirectory(workingDir.getPath());

      String shellScriptFilePath = FileUtil.toSystemDependentName(myShellScriptFile.getPath());
      if (VfsUtilCore.virtualToIoFile(myShellScriptFile).canExecute()) {
        commandLine.setExePath(shellScriptFilePath);
      }
      else {
        commandLine.setExePath(ObjectUtils.notNull(ShRunner.getShebangExecutable(getBashFile()), getDefaultShell()));
        commandLine.addParameter(shellScriptFilePath);
      }
      return commandLine;
    }

    @NotNull
    private static String getDefaultShell() {
      return ObjectUtils.notNull(EnvironmentUtil.getValue("SHELL"), "/bin/sh");
    }

    @NotNull
    private ShFile getBashFile() throws ExecutionException {
      ShFile shFile = ObjectUtils.tryCast(PsiManager.getInstance(myProject).findFile(myShellScriptFile), ShFile.class);
      if (shFile == null) {
        throw new ExecutionException("Cannot find BashFile by " + myShellScriptFile.getPath());
      }
      return shFile;
    }
  }
}
