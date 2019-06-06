// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.Alarm;
import com.intellij.util.SingleAlarm;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;

public class ShFailoverRunnerUtil {
  private ShFailoverRunnerUtil() {}

  static ExecutionResult buildExecutionResult(@NotNull Project project, @NotNull GeneralCommandLine commandLine) throws ExecutionException {
    ProcessHandler processHandler = createProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    ConsoleView console = new TerminalExecutionConsole(project, processHandler);
    console.attachToProcess(processHandler);

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        new SingleAlarm(() -> ReadAction.run(() -> {
          if (!project.isDisposed()) {
            LocalFileSystem.getInstance().refresh(true);
          }
        }), 300, Alarm.ThreadToUse.POOLED_THREAD, project).request();
      }
    });
    return new DefaultExecutionResult(console, processHandler);
  }

  @NotNull
  private static ProcessHandler createProcessHandler(GeneralCommandLine commandLine) throws ExecutionException {
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
}
