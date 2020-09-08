// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.*;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PyCythonExtensionWarning {
  private static final Logger LOG = Logger.getInstance(PyCythonExtensionWarning.class);

  private static final String CYTHON_WARNING_GROUP_ID = "CythonWarning";
  public static final String SETUP_CYTHON_PATH = "pydev/setup_cython.py";


  public static void showCythonExtensionWarning(@NotNull Project project) {
    if (shouldSuppressNotification(project)) {
      return;
    }
    Notification notification =
      new Notification(CYTHON_WARNING_GROUP_ID, PyBundle.message("compile.cython.extensions.notification"),
                       PyBundle.message("debugger.cython.extension.speeds.up.python.debugging"),
                       NotificationType.INFORMATION);
    notification.addAction(createInstallAction(notification, project));
    notification.addAction(createDocsAction());
    notification.notify(project);
  }

  private static AnAction createInstallAction(@NotNull Notification notification, @NotNull Project project) {
    return new DumbAwareAction(PyBundle.message("compile.cython.extensions.install")) {

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        compileCythonExtension(project);
        notification.expire();
      }
    };
  }

  private static AnAction createDocsAction() {
    return new DumbAwareAction(PyBundle.message("compile.cython.extensions.help")) {

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        HelpManager.getInstance().invokeHelp("Cython_Speedups");
      }
    };
  }

  private static boolean shouldSuppressNotification(@NotNull Project project) {
    final RunManager runManager = RunManager.getInstance(project);
    final RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
    if (selectedConfiguration == null) {
      return true;
    }
    final RunConfiguration configuration = selectedConfiguration.getConfiguration();
    if (!(configuration instanceof AbstractPythonRunConfiguration)) {
      return true;
    }
    AbstractPythonRunConfiguration runConfiguration = (AbstractPythonRunConfiguration)configuration;
    // Temporarily disable notification for Remote interpreters
    return PythonSdkUtil.isRemote(runConfiguration.getSdk());
  }

  private static void showErrorDialog(Project project, @DialogMessage String message) {
    Messages.showMessageDialog(project, message, PyBundle.message("compile.cython.extensions.error"), null);
  }

  private static void compileCythonExtension(@NotNull Project project) {
    try {
      final RunManager runManager = RunManager.getInstance(project);
      final RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
      if (selectedConfiguration == null) {
        throw new ExecutionException(PyBundle.message("debugger.cython.python.run.configuration.should.be.selected"));
      }
      final RunConfiguration configuration = selectedConfiguration.getConfiguration();
      if (!(configuration instanceof AbstractPythonRunConfiguration)) {
        throw new ExecutionException(PyBundle.message("debugger.cython.python.run.configuration.should.be.selected"));
      }
      AbstractPythonRunConfiguration runConfiguration = (AbstractPythonRunConfiguration)configuration;
      final String interpreterPath = runConfiguration.getInterpreterPath();
      final String helpersPath = PythonHelpersLocator.getHelpersRoot().getPath();

      final String cythonExtensionsDir = PyDebugRunner.CYTHON_EXTENSIONS_DIR;
      final String[] cythonArgs =
        {"build_ext", "--build-lib", cythonExtensionsDir, "--build-temp", String.format("%s%sbuild", cythonExtensionsDir, File.separator)};

      final List<String> cmdline = new ArrayList<>();
      cmdline.add(interpreterPath);
      cmdline.add(FileUtil.join(helpersPath, FileUtil.toSystemDependentName(SETUP_CYTHON_PATH)));
      cmdline.addAll(Arrays.asList(cythonArgs));
      LOG.info("Compile Cython Extensions " + StringUtil.join(cmdline, " "));

      final Map<String, String> environment = new HashMap<>(System.getenv());
      PythonEnvUtil.addToPythonPath(environment, cythonExtensionsDir);
      PythonEnvUtil.setPythonUnbuffered(environment);
      PythonEnvUtil.setPythonDontWriteBytecode(environment);
      if (interpreterPath != null) {
        PythonEnvUtil.resetHomePathChanges(interpreterPath, environment);
      }
      GeneralCommandLine commandLine = new GeneralCommandLine(cmdline).withEnvironment(environment);

      final boolean canCreate = FileUtil.ensureCanCreateFile(new File(cythonExtensionsDir));
      final boolean useSudo = !canCreate && !SystemInfo.isWindows;
      Process process;
      if (useSudo) {
        process = ExecUtil.sudo(commandLine, PyBundle.message("debugger.cython.please.enter.your.password.to.compile.cython.extensions"));
      }
      else {
        process = commandLine.createProcess();
      }

      ProgressManager.getInstance().run(new Task.Backgroundable(project, PyBundle.message("compile.cython.extensions.title")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final CapturingProcessHandler handler =
            new CapturingProcessHandler(process, commandLine.getCharset(), commandLine.getCommandLineString());
          handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
              if (outputType == ProcessOutputTypes.STDOUT || outputType == ProcessOutputTypes.STDERR) {
                for (String line : StringUtil.splitByLines(event.getText())) {
                  if (isSignificantOutput(line)) {
                    indicator.setText2(line.trim()); //NON-NLS
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
                                   ? PyBundle.message("debugger.cython.extension.permission.denied")
                                   : PyBundle.message("debugger.cython.extension.non.zero.exit.code", exitCode, result.getStderr());
            UIUtil.invokeLaterIfNeeded(() -> showErrorDialog(project, message));
          }
        }
      });
    }
    catch (IOException | ExecutionException e) {
      showErrorDialog(project, e.getMessage());
    }
  }
}
