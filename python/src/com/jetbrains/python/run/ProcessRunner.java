/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.run;

import com.intellij.execution.*;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg, Roman Chernyatchik
 *         date 24.08.2006
 */
public class ProcessRunner {

  private ProcessRunner() {
  }

  /**
   * Returns output after execution.
   *
   * @param project
   * @param workingDir working directory
   * @param showErrors
   * @param command    Command to execute @return Output object
   * @return Output
   */
  @NotNull
  public static Output runInPath(@Nullable final Project project,
                                 @Nullable final String workingDir,
                                 final boolean showErrors,
                                 @Nullable final Map<String, String> envs,
                                 @NotNull final String... command) throws ExecutionException {
    // executing
    final StringBuilder out = new StringBuilder();
    final StringBuilder err = new StringBuilder();
    Process process = createProcess(workingDir, envs, command);
    //TODO[romeo for troff] refactor - create process should return GeneralCommandLine and actual cmd line should be get from it
    final String commandLine = StringUtil.join(command, " ");
    final ColoredProcessHandler osProcessHandler = new ColoredProcessHandler(process, commandLine);
    osProcessHandler.addProcessListener(new OutputListener(out, err));
    osProcessHandler.startNotify();

    ExecutionHelper.executeExternalProcess(project, osProcessHandler, new ExecutionModes.ModalProgressMode(null), commandLine);

    final Output output = new Output(out.toString(), err.toString());
    if (showErrors && !StringUtil.isEmpty(output.getStderr())) {
      assert project != null;
      final String tabName = "Unknown error";

      final List<Exception> errorList = new LinkedList<>();
      //noinspection ThrowableInstanceNeverThrown
      errorList.add(new Exception(output.getStderr()));

      final VirtualFile executableFile = LocalFileSystem.getInstance().findFileByPath(command[0]);
      ExecutionHelper.showErrors(project, errorList, tabName, executableFile);
    }
    return output;
  }


  /**
   * Creates add by command and working directory
   *
   * @param command    add command line
   * @param workingDir add working directory or null, if no special needed
   * @return add
   */
  @Nullable
  public static Process createProcess(@Nullable final String workingDir, @NotNull final String... command) throws ExecutionException {
    return createProcess(workingDir, null, command);
  }

  @Nullable
  public static Process createProcess(@Nullable final String workingDir,
                                      @Nullable Map<String, String> additionalEnvs,
                                      @NotNull final String... command) throws ExecutionException {
    final String[] arguments;
    if (command.length > 1) {
      arguments = new String[command.length - 1];
      System.arraycopy(command, 1, arguments, 0, command.length - 1);
    }
    else {
      arguments = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    final GeneralCommandLine cmdLine = createAndSetupCmdLine(workingDir, additionalEnvs, true, command[0],
                                                             arguments);
    return cmdLine.createProcess();
  }

  /**
   * Creates process builder and setups it's commandLine, working directory, environment variables
   *
   * @param workingDir         Process working dir
   * @param executablePath     Path to executable file
   * @param arguments          Process commandLine  @return process builder
   */
  public static GeneralCommandLine createAndSetupCmdLine(@Nullable final String workingDir,
                                                         @Nullable final Map<String, String> userDefinedEnv,
                                                         final boolean passParentEnv,
                                                         @NotNull final String executablePath,
                                                         @NotNull final String... arguments) {
    GeneralCommandLine cmdLine = new GeneralCommandLine();

    cmdLine.setExePath(toSystemDependentName(executablePath));

    if (workingDir != null) {
      cmdLine.setWorkDirectory(toSystemDependentName(workingDir));
    }

    cmdLine.addParameters(arguments);

    cmdLine.withParentEnvironmentType(passParentEnv ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);
    cmdLine.withEnvironment(userDefinedEnv);
    //Inline parent env variables occurrences
    EnvironmentUtil.inlineParentOccurrences(cmdLine.getEnvironment());

    return cmdLine;
  }
}
