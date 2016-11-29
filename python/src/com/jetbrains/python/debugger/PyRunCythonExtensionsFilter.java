/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.*;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PyRunCythonExtensionsFilter implements Filter {
  public static final String ERROR_TITLE = "Compile Cython Extensions Error";
  public static final String WARNING_MESSAGE_BEGIN = "warning: Debugger speedups using cython not found. Run \'";
  public static final String WARNING_MESSAGE_END = "\' to build.";
  public static final String SETUP_CYTHON_PATH = "pydev/setup_cython.py";
  public static final String[] CYTHON_ARGS = {"build_ext", "--inplace"};
  private static final Logger LOG = Logger.getInstance("com.jetbrains.python.debugger.PyRunCythonExtensionsFilter");
  private final @NotNull Project myProject;
  private volatile boolean isApplied = false;

  public PyRunCythonExtensionsFilter(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    final int indexOfWarning = line.indexOf(WARNING_MESSAGE_BEGIN);
    if (indexOfWarning != -1) {
      final int indexOfLinkBeginning = indexOfWarning + WARNING_MESSAGE_BEGIN.length();
      final int textStartOffset = entireLength - line.length();
      final int indexOfLinkEnd = line.indexOf(WARNING_MESSAGE_END);
      return new Result(textStartOffset + indexOfLinkBeginning, textStartOffset + indexOfLinkEnd, new DebuggerExtensionsHyperlinkInfo());
    }
    return null;
  }

  private void showErrorDialog(String message) {
    Messages.showMessageDialog(myProject, message, ERROR_TITLE, null);
  }

  private class DebuggerExtensionsHyperlinkInfo implements HyperlinkInfo {
    @Override
    public void navigate(Project project) {
      try {
        if (isApplied) {
          return;
        }
        final RunManager runManager = RunManager.getInstance(myProject);
        final RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
        if (selectedConfiguration == null) {
          throw new ExecutionException("Python Run Configuration should be selected");
        }
        final RunConfiguration configuration = selectedConfiguration.getConfiguration();
        if (!(configuration instanceof AbstractPythonRunConfiguration)) {
          throw new ExecutionException("Python Run Configuration should be selected");
        }
        AbstractPythonRunConfiguration runConfiguration = (AbstractPythonRunConfiguration)configuration;
        final String sdkPath = runConfiguration.getSdkHome();
        final String helpersPath = PythonHelpersLocator.getHelpersRoot().getPath();

        final List<String> cmdline = new ArrayList<>();
        cmdline.add(sdkPath);
        cmdline.add(FileUtil.join(helpersPath, FileUtil.toSystemDependentName(SETUP_CYTHON_PATH)));
        cmdline.addAll(Arrays.asList(CYTHON_ARGS));
        LOG.info("Compile Cython Extensions " + StringUtil.join(cmdline, " "));

        final Map<String, String> environment = new HashMap<>(System.getenv());
        PythonEnvUtil.setPythonUnbuffered(environment);
        PythonEnvUtil.setPythonDontWriteBytecode(environment);
        if (sdkPath != null) {
          PythonEnvUtil.resetHomePathChanges(sdkPath, environment);
        }
        GeneralCommandLine commandLine = new GeneralCommandLine(cmdline).withEnvironment(environment);

        final boolean canCreate = FileUtil.ensureCanCreateFile(new File(helpersPath));
        final boolean useSudo = !canCreate && !SystemInfo.isWindows;
        Process process;
        if (useSudo) {
          process = ExecUtil.sudo(commandLine, "Please enter your password to compile cython extensions: ");
        }
        else {
          process = commandLine.createProcess();
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Compile Cython Extensions") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            isApplied = true;
            final CapturingProcessHandler handler =
              new CapturingProcessHandler(process, commandLine.getCharset(), commandLine.getCommandLineString());
            handler.addProcessListener(new ProcessAdapter() {
              @Override
              public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (outputType == ProcessOutputTypes.STDOUT || outputType == ProcessOutputTypes.STDERR) {
                  for (String line : StringUtil.splitByLines(event.getText())) {
                    if (isSignificantOutput(line)) {
                      indicator.setText2(line.trim());
                    }
                  }
                }
              }

              private boolean isSignificantOutput(String line) {
                return line.trim().length() > 3;
              }
            });
            final ProcessOutput result = handler.runProcessWithProgressIndicator(indicator);
            final int exitCode = result.getExitCode();
            if (exitCode != 0) {
              final String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr())
                                     ? "Permission denied"
                                     : "Non-zero exit code (" + exitCode + "): \n" + result.getStderr();
              UIUtil.invokeLaterIfNeeded(() -> showErrorDialog(message));
            }
          }
        });
      }
      catch (IOException e) {
        showErrorDialog(e.getMessage());
      }
      catch (ExecutionException e) {
        showErrorDialog(e.getMessage());
      }
    }
  }
}
