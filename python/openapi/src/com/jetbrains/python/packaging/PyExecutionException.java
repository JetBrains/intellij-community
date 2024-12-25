// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionExceptionWithAttachments;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PyExecutionException extends ExecutionExceptionWithAttachments {
  private final @NotNull String myCommand;
  private final @NotNull List<String> myArgs;
  private final int myExitCode;
  private final @NotNull List<? extends PyExecutionFix> myFixes;

  public PyExecutionException(@DialogMessage @NotNull String message, @NotNull String command, @NotNull List<String> args) {
    this(message, command, args, "", "", 0, Collections.emptyList());
  }

  public PyExecutionException(@DialogMessage @NotNull String message,
                              @NotNull String command,
                              @NotNull List<String> args,
                              @NotNull ProcessOutput output) {
    this(message, command, args, output.getStdout(), output.getStderr(), output.getExitCode(), Collections.emptyList());
  }

  public PyExecutionException(@DialogMessage @NotNull String message, @NotNull String command, @NotNull List<String> args,
                              @NotNull String stdout, @NotNull String stderr, int exitCode,
                              @NotNull List<? extends PyExecutionFix> fixes) {
    super(message, stdout, stderr);
    myCommand = command;
    myArgs = args;
    myExitCode = exitCode;
    myFixes = fixes;
  }

  @Override
  public String toString() {
    final StringBuilder b = new StringBuilder();
    b.append("The following command was executed:\n\n");
    final String command = getCommand() + " " + StringUtil.join(getArgs(), " ");
    b.append(command);
    b.append("\n\n");
    b.append("The exit code: ").append(myExitCode).append("\n");
    b.append("The error output of the command:\n\n");
    b.append(getStdout());
    b.append("\n");
    b.append(getStderr());
    b.append("\n");
    b.append(getMessage());
    return b.toString();
  }

  public @NotNull String getCommand() {
    return myCommand;
  }

  public @NotNull List<String> getArgs() {
    return myArgs;
  }

  public @NotNull List<? extends PyExecutionFix> getFixes() {
    return myFixes;
  }

  public int getExitCode() {
    return myExitCode;
  }
}