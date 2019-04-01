package com.intellij.bash.run;

import com.intellij.bash.psi.BashFile;
import com.intellij.execution.*;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.Alarm;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SingleAlarm;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FailoverShellScriptRunner extends ShellScriptRunner {
  @Override
  public void run(@NotNull BashFile bashFile) {
    Project project = bashFile.getProject();
    ExecutionEnvironmentBuilder builder = new ExecutionEnvironmentBuilder(project, DefaultRunExecutor.getRunExecutorInstance())
        .runProfile(new RunProfile() {
          @Override
          public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
            return new MyRunProfileState(bashFile.getProject(), bashFile.getVirtualFile());
          }

          @NotNull
          @Override
          public String getName() {
            return "Run " + bashFile.getName();
          }

          @Override
          public Icon getIcon() {
            return AllIcons.Actions.Install;
          }
        });
    ExecutionManager.getInstance(project).restartRunProfile(builder.build());
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
      ProcessHandler processHandler = createProcessHandler(commandLine);
      ProcessTerminatedListener.attach(processHandler);
      ConsoleView console = new TerminalExecutionConsole(myProject, processHandler);
      console.attachToProcess(processHandler);

      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          new SingleAlarm(() -> ReadAction.run(() -> {
            if (!myProject.isDisposed()) {
              LocalFileSystem.getInstance().refresh(true);
            }
          }), 300, Alarm.ThreadToUse.POOLED_THREAD, myProject).request();
        }
      });
      return new DefaultExecutionResult(console, processHandler);
    }

    @NotNull
    private ProcessHandler createProcessHandler(GeneralCommandLine commandLine) throws ExecutionException {
      return new KillableProcessHandler(commandLine) {
        @NotNull
        @Override
        protected BaseOutputReader.Options readerOptions() {
          return new BaseOutputReader.Options() {
            @Override
            public BaseDataReader.SleepingPolicy policy() {
              return BaseDataReader.SleepingPolicy.BLOCKING;
            }

            @Override
            public boolean splitToLines() {
              return false;
            }

            @Override
            public boolean withSeparators() {
              return true;
            }
          };
        }
      };
    }

    @NotNull
    private GeneralCommandLine createCommandLine(@NotNull VirtualFile workingDir) throws ExecutionException {
      PtyCommandLine commandLine = new PtyCommandLine();
      if (!SystemInfo.isWindows) {
        commandLine.getEnvironment().put("TERM", "xterm-256color");
      }
      commandLine.withConsoleMode(false);
      commandLine.withInitialColumns(120);
      commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
      commandLine.setWorkDirectory(workingDir.getPath());

      String shellScriptFilePath = FileUtil.toSystemDependentName(myShellScriptFile.getPath());
      if (VfsUtil.virtualToIoFile(myShellScriptFile).canExecute()) {
        commandLine.setExePath(shellScriptFilePath);
      }
      else {
        commandLine.setExePath(ObjectUtils.notNull(ShellScriptRunner.getShebangExecutable(getBashFile()), getDefaultShell()));
        commandLine.addParameter(shellScriptFilePath);
      }
      return commandLine;
    }

    @NotNull
    private static String getDefaultShell() {
      return ObjectUtils.notNull(EnvironmentUtil.getValue("SHELL"), "/bin/bash");
    }

    @NotNull
    private BashFile getBashFile() throws ExecutionException {
      BashFile bashFile = ObjectUtils.tryCast(PsiManager.getInstance(myProject).findFile(myShellScriptFile), BashFile.class);
      if (bashFile == null) {
        throw new ExecutionException("Cannot find BashFile by " + myShellScriptFile.getPath());
      }
      return bashFile;
    }
  }
}
