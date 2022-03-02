// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.markdown;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.run.ShConfigurationType;
import com.intellij.sh.run.ShRunner;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.ObjectUtils;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShMarkdownRunner implements MarkdownRunner {
  @Override
  public boolean isApplicable(Language language) {
    return language != null && language.is(ShLanguage.INSTANCE);
  }

  @Override
  public boolean run(@NotNull String command,
                     @NotNull Project project,
                     @Nullable String workingDirectory, @NotNull Executor executor) {
    ShRunner shRunner = ApplicationManager.getApplication().getService(ShRunner.class);
    if (shRunner != null && shRunner.isAvailable(project)) {
      shRunner.run(project, command, workingDirectory, "RunMarkdown", true);
    }
    /*try {
      runInTerminal(command, workingDirectory, project);
    }
    catch (ExecutionException e) {
      e.printStackTrace();
    }*/
    return true;
  }

  @NotNull
  @Override
  public String title() {
    return ShBundle.message("sh.markdown.runner.title");
  }

  private DefaultExecutionResult runInTerminal(String command, String workingDirectory, Project project) throws ExecutionException {
    GeneralCommandLine commandLine = createCommandLineForScript(workingDirectory, command);
    ProcessHandler processHandler = createProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    ConsoleView console = new TerminalExecutionConsole(project, processHandler);
    console.attachToProcess(processHandler);
    return new DefaultExecutionResult(console, processHandler);
  }

  @NotNull
  private GeneralCommandLine createCommandLineForScript(String workingDirectory,  String command) {
    PtyCommandLine commandLine = new PtyCommandLine();
    commandLine.withConsoleMode(false);
    commandLine.withInitialColumns(120);
    commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    commandLine.setWorkDirectory(workingDirectory);
    commandLine.withExePath(ObjectUtils.notNull(ShConfigurationType.getDefaultShell(), "/bin/sh"));
    commandLine.withParameters("-c");
    commandLine.withParameters(command);
    return commandLine;
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
        };
      }
    };
  }


}
