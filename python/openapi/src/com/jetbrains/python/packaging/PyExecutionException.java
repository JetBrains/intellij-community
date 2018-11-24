/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyExecutionException extends ExecutionException {
  @NotNull private String myCommand;
  @NotNull private List<String> myArgs;
  @NotNull private final String myStdout;
  @NotNull private final String myStderr;
  private final int myExitCode;
  @NotNull private final List<? extends PyExecutionFix> myFixes;

  public PyExecutionException(@NotNull String message, @NotNull String command, @NotNull List<String> args) {
    this(message, command, args, "", "", 0, Collections.<PyExecutionFix>emptyList());
  }

  public PyExecutionException(@NotNull String message, @NotNull String command, @NotNull List<String> args, @NotNull ProcessOutput output) {
    this(message, command, args, output.getStdout(), output.getStderr(), output.getExitCode(), Collections.<PyExecutionFix>emptyList());
  }

  public PyExecutionException(@NotNull String message, @NotNull String command, @NotNull List<String> args,
                              @NotNull String stdout, @NotNull String stderr, int exitCode,
                              @NotNull List<? extends PyExecutionFix> fixes) {
    super(message);
    myCommand = command;
    myArgs = args;
    myStdout = stdout;
    myStderr = stderr;
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
    b.append(myStdout);
    b.append("\n");
    b.append(myStderr);
    return b.toString();
  }

  @NotNull
  public String getCommand() {
    return myCommand;
  }

  @NotNull
  public List<String> getArgs() {
    return myArgs;
  }

  @NotNull
  public List<? extends PyExecutionFix> getFixes() {
    return myFixes;
  }

  public int getExitCode() {
    return myExitCode;
  }

  @NotNull
  public String getStdout() {
    return myStdout;
  }

  @NotNull
  public String getStderr() {
    return myStderr;
  }
}
