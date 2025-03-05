// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.jetbrains.python.execution.FailureReason;
import com.jetbrains.python.execution.FailureReasonKt;
import com.jetbrains.python.execution.PyExecutionFailure;
import com.jetbrains.python.execution.PyExecutionFailureKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Process execution failed.
 * There are two cases, see {@link FailureReason}.
 * Each constructor represents one or another.
 *
 * @see FailureReason
 */
public final class PyExecutionException extends ExecutionException implements PyExecutionFailure {
  private final @NotNull String myCommand;
  private final @NotNull List<String> myArgs;
  private final @NotNull List<? extends PyExecutionFix> myFixes;
  private final @DialogMessage @Nullable String myAdditionalMessage;
  private final @NotNull FailureReason myError;

  /**
   * A process failed to start, {@link FailureReason.CantStart}
   *
   * @param additionalMessage a process start reason for a user
   */
  public PyExecutionException(@DialogMessage @Nullable String additionalMessage,
                              @NotNull String command,
                              @NotNull List<String> args) {
    this(additionalMessage, command, args, Collections.emptyList());
  }

  /**
   * A process failed to start, {@link FailureReason.CantStart}
   *
   * @param additionalMessage a process start reason for a user
   */
  public PyExecutionException(@DialogMessage @Nullable String additionalMessage,
                              @NotNull String command,
                              @NotNull List<String> args,
                              @NotNull List<? extends PyExecutionFix> fixes) {
    super(PyExecutionFailureKt.getUserMessage(command, args, additionalMessage, FailureReason.CantStart.INSTANCE));
    myAdditionalMessage = additionalMessage;
    myCommand = command;
    myArgs = args;
    myFixes = fixes;
    myError = FailureReason.CantStart.INSTANCE;
  }

  /**
   * A process started, but failed {@link FailureReason.ExecutionFailed}
   *
   * @param additionalMessage a process start reason for a user
   * @param output            execution output
   */
  public PyExecutionException(@DialogMessage @Nullable String additionalMessage,
                              @NotNull String command,
                              @NotNull List<String> args,
                              @NotNull ProcessOutput output) {
    this(additionalMessage, command, args, output, Collections.emptyList());
  }

  /**
   * A process started, but failed {@link FailureReason.ExecutionFailed}
   *
   * @param additionalMessage a process start reason for a user
   * @param output            execution output
   */
  public PyExecutionException(@DialogMessage @Nullable String additionalMessage,
                              @NotNull String command,
                              @NotNull List<String> args,
                              @NotNull ProcessOutput output,
                              @NotNull List<? extends PyExecutionFix> fixes) {
    super(PyExecutionFailureKt.getUserMessage(command, args, additionalMessage, new FailureReason.ExecutionFailed(output)));
    myAdditionalMessage = additionalMessage;
    myCommand = command;
    myArgs = args;
    myFixes = fixes;
    myError = new FailureReason.ExecutionFailed(output);
  }

  /**
   * A process started, but failed {@link FailureReason.ExecutionFailed}
   *
   * @param additionalMessage a process start reason for a user
   */
  public PyExecutionException(@DialogMessage @Nullable String additionalMessage,
                              @NotNull String command,
                              @NotNull List<String> args,
                              @NotNull String stdout,
                              @NotNull String stderr,
                              int exitCode,
                              @NotNull List<? extends PyExecutionFix> fixes) {
    this(additionalMessage, command, args, new ProcessOutput(stdout, stderr, exitCode, false, false), fixes);
  }

  @Override
  public @NotNull String getCommand() {
    return myCommand;
  }

  @Override
  public @NotNull List<String> getArgs() {
    return myArgs;
  }

  public @NotNull List<? extends PyExecutionFix> getFixes() {
    return myFixes;
  }

  @Override
  public @Nullable String getAdditionalMessage() {
    return myAdditionalMessage;
  }

  @Override
  public @NotNull FailureReason getFailureReason() {
    return myError;
  }

  /**
   * @deprecated use {@link #getFailureReason()} and match it as when process failed to start there is no exit code
   */
  @Deprecated(forRemoval = true)
  public int getExitCode() {
    if (getFailureReason() instanceof FailureReason.ExecutionFailed executionFailed) {
      return executionFailed.getOutput().getExitCode();
    }
    return -1;
  }

  @ApiStatus.Internal
  @NotNull
  public PyExecutionException copyWith(@NotNull String newCommand, @NotNull List<@NotNull String> newArgs) {
    return FailureReasonKt.copyWith(this, newCommand, newArgs);
  }
}