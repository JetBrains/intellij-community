// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PyVirtualEnvReader;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A more flexible cousin of SdkVersionUtil.
 * Needs not to be instantiated and only holds static methods.
 *
 * @author dcheryasov
 * @see PythonSdkUtil for Pyhton SDK utilities with no run-time dependencies
 */
//TODO: rename to PySdkExecuteUtil or PySdkRuntimeUtil
public final class PySdkUtil {
  protected static final Logger LOG = Logger.getInstance(PySdkUtil.class);

  // Windows EOF marker, Ctrl+Z
  public static final int SUBSTITUTE = 26;
  public static final String PATH_ENV_VARIABLE = "PATH";
  private static final Key<Map<String, String>> ENVIRONMENT_KEY = Key.create("ENVIRONMENT_KEY");

  private PySdkUtil() {
    // explicitly none
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   *
   * @param homePath process run directory
   * @param command  command to execute and its arguments
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
   */
  @NotNull
  public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command) {
    return getProcessOutput(homePath, command, -1);
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   * Waits for process for possibly limited duration.
   *
   * @param homePath process run directory
   * @param command  command to execute and its arguments
   * @param timeout  how many milliseconds to wait until the process terminates; non-positive means inifinity.
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
   */
  @NotNull
  public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command, final int timeout) {
    return getProcessOutput(homePath, command, null, timeout);
  }

  @NotNull
  public static ProcessOutput getProcessOutput(String homePath,
                                               @NonNls String[] command,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               final int timeout) {
    return getProcessOutput(homePath, command, extraEnv, timeout, null, true);
  }

  @NotNull
  public static ProcessOutput getProcessOutput(String homePath,
                                               @NonNls String[] command,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               final int timeout,
                                               byte @Nullable [] stdin,
                                               boolean needEOFMarker) {
    return getProcessOutput(new GeneralCommandLine(command), homePath, extraEnv, timeout, stdin, needEOFMarker);
  }

  public static ProcessOutput getProcessOutput(@NotNull GeneralCommandLine cmd, @Nullable String homePath,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               int timeout) {
    return getProcessOutput(cmd, homePath, extraEnv, timeout, null, true);
  }

  public static ProcessOutput getProcessOutput(@NotNull GeneralCommandLine cmd, @Nullable String homePath,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               int timeout,
                                               byte @Nullable [] stdin, boolean needEOFMarker) {
    if (homePath == null || !new File(homePath).exists()) {
      return new ProcessOutput();
    }
    final Map<String, String> systemEnv = System.getenv();
    final Map<String, String> expandedCmdEnv = mergeEnvVariables(systemEnv, cmd.getEnvironment());
    final Map<String, String> env = extraEnv != null ? mergeEnvVariables(expandedCmdEnv, extraEnv) : expandedCmdEnv;
    PythonEnvUtil.resetHomePathChanges(homePath, env);
    try {

      final GeneralCommandLine commandLine = cmd.withWorkDirectory(homePath).withEnvironment(env);

      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(commandLine.getExePath());

      final CommandLinePatcher cmdLinePatcher = flavor != null ? flavor.commandLinePatcher() : null;
      if (cmdLinePatcher != null) {
        cmdLinePatcher.patchCommandLine(cmd);
      }

      final CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine);
      if (stdin != null) {
        final OutputStream processInput = processHandler.getProcessInput();
        assert processInput != null;
        processInput.write(stdin);
        if (SystemInfo.isWindows && needEOFMarker) {
          processInput.write(SUBSTITUTE);
          processInput.flush();
        }
        else {
          processInput.close();
        }
      }
      if (SwingUtilities.isEventDispatchThread()) {
        final ProgressManager progressManager = ProgressManager.getInstance();
        final Application application = ApplicationManager.getApplication();
        assert application.isUnitTestMode() || application.isHeadlessEnvironment() || !application.isWriteAccessAllowed() : "Background task can't be run under write action";
        return progressManager.runProcessWithProgressSynchronously(() -> processHandler.runProcess(timeout),
                                                                   PySdkBundle.message("python.sdk.run.wait"), false, null);
      }
      else {
        return processHandler.runProcess();
      }
    }
    catch (ExecutionException | IOException e) {
      return getOutputForException(e);
    }
  }

  private static ProcessOutput getOutputForException(final Exception e) {
    LOG.warn(e);
    return new ProcessOutput() {
      @NotNull
      @Override
      public String getStderr() {
        String err = super.getStderr();
        if (!StringUtil.isEmpty(err)) {
          err += "\n" + e.getMessage();
        }
        else {
          err = e.getMessage();
        }
        return err;
      }
    };
  }

  @NotNull
  public static Map<String, String> mergeEnvVariables(@NotNull Map<String, String> environment,
                                                      @NotNull Map<String, String> extraEnvironment) {
    final Map<String, String> result = new HashMap<>(environment);
    for (Map.Entry<String, String> entry : extraEnvironment.entrySet()) {
      final String name = entry.getKey();
      if (PATH_ENV_VARIABLE.equals(name) || PythonEnvUtil.PYTHONPATH.equals(name)) {
        PythonEnvUtil.addPathToEnv(result, name, entry.getValue());
      }
      else {
        result.put(name, entry.getValue());
      }
    }
    return result;
  }

  @NotNull
  public static Map<String, String> activateVirtualEnv(@NotNull Sdk sdk) {
    final Map<String, String> cached = sdk.getUserData(ENVIRONMENT_KEY);
    if (cached != null) return cached;

    final String sdkHome = sdk.getHomePath();
    if (sdkHome == null) return Collections.emptyMap();

    final Map<String, String> environment = activateVirtualEnv(sdkHome);
    sdk.putUserData(ENVIRONMENT_KEY, environment);
    return environment;
  }

  @NotNull
  public static Map<String, String> activateVirtualEnv(@NotNull String sdkHome) {
    PyVirtualEnvReader reader = new PyVirtualEnvReader(sdkHome);
    if (reader.getActivate() != null) {
      try {
        return Collections.unmodifiableMap(PyVirtualEnvReader.Companion.filterVirtualEnvVars(reader.readPythonEnv()));
      }
      catch (Exception e) {
        LOG.error("Couldn't read virtualenv variables", e);
      }
    }

    return Collections.emptyMap();
  }
}
