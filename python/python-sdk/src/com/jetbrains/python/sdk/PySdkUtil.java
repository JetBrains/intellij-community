// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PyVirtualEnvReader;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A more flexible cousin of SdkVersionUtil.
 * Needs not to be instantiated and only holds static methods.
 *
 * @see PythonSdkUtil for Pyhton SDK utilities with no run-time dependencies
 *
 * @deprecated please use Kotlin coroutines to run processes in background
 */
@Deprecated(forRemoval = true)
public final class PySdkUtil {
  private static final Logger LOG = Logger.getInstance(PySdkUtil.class);

  // Windows EOF marker, Ctrl+Z
  public static final int SUBSTITUTE = 26;
  public static final String PATH_ENV_VARIABLE = "PATH";
  private static final Key<Map<String, String>> ENVIRONMENT_KEY = Key.create("ENVIRONMENT_KEY");

  private PySdkUtil() {
    // explicitly none
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
  @ApiStatus.Internal

  public static @NotNull ProcessOutput getProcessOutput(String homePath, @NonNls String[] command, final int timeout) {
    return getProcessOutput(homePath, command, null, timeout);
  }

  @ApiStatus.Internal

  public static @NotNull ProcessOutput getProcessOutput(String homePath,
                                                        @NonNls String[] command,
                                                        @Nullable @NonNls Map<String, String> extraEnv,
                                                        final int timeout) {
    return getProcessOutput(homePath, command, extraEnv, timeout, null, true);
  }

  @ApiStatus.Internal

  public static @NotNull ProcessOutput getProcessOutput(String homePath,
                                                        @NonNls String[] command,
                                                        @Nullable @NonNls Map<String, String> extraEnv,
                                                        final int timeout,
                                                        byte @Nullable [] stdin,
                                                        boolean needEOFMarker) {
    return getProcessOutput(new GeneralCommandLine(command), homePath, extraEnv, timeout, stdin, needEOFMarker);
  }

  @ApiStatus.Internal

  public static ProcessOutput getProcessOutput(@NotNull GeneralCommandLine cmd, @Nullable String homePath,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               int timeout) {
    return getProcessOutput(cmd, homePath, extraEnv, timeout, null, true);
  }

  public static ProcessOutput getProcessOutput(@NotNull GeneralCommandLine cmd, @Nullable String homePath,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               int timeout,
                                               byte @Nullable [] stdin, boolean needEOFMarker) {
    return getProcessOutput(cmd, homePath, extraEnv, timeout, stdin, needEOFMarker, null);
  }

  @ApiStatus.Internal

  public static ProcessOutput getProcessOutput(@NotNull GeneralCommandLine cmd, @Nullable String homePath,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               int timeout,
                                               byte @Nullable [] stdin, boolean needEOFMarker,
                                               @Nullable @Nls(capitalization = Nls.Capitalization.Title) String customTitle) {
    if (homePath == null || !new File(homePath).exists()) {
      return new ProcessOutput();
    }
    final Map<String, String> systemEnv = EnvironmentUtil.getEnvironmentMap();
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
        assert application.isUnitTestMode() ||
               application.isHeadlessEnvironment() ||
               !application.isWriteAccessAllowed() : "Background task can't be run under write action";
        String dialogTitle = customTitle != null ? customTitle : PySdkBundle.message("python.sdk.run.wait");
        return progressManager.runProcessWithProgressSynchronously(() -> processHandler.runProcess(timeout), dialogTitle , false, null);
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
      @Override
      public @NotNull String getStderr() {
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

  public static @NotNull Map<String, String> mergeEnvVariables(@NotNull Map<String, String> environment,
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

  public static @NotNull Map<String, String> activateVirtualEnv(@NotNull Sdk sdk) {
    final Map<String, String> cached = sdk.getUserData(ENVIRONMENT_KEY);
    if (cached != null) return cached;

    final String sdkHome = sdk.getHomePath();
    if (sdkHome == null || sdkHome.trim().isEmpty()) {
      // homePath is empty (not null) by default.
      // If we cache values when path is empty, we would stuck with empty env and never reread it once path set
      LOG.warn("homePath is null or empty, skipping env loading for " + sdk.getName());
      return Collections.emptyMap();
    }

    var additionalData = ObjectUtils.tryCast(sdk.getSdkAdditionalData(), PythonSdkAdditionalData.class);
    if (additionalData == null) {
      return Collections.emptyMap();
    }
    final Map<String, String> environment = activateVirtualEnv(sdkHome);
    sdk.putUserData(ENVIRONMENT_KEY, environment);
    return environment;
  }

  /**
   * @deprecated doesn't support targets
   */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public static @NotNull Map<String, String> activateVirtualEnv(@NotNull String sdkHome) {
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

  public static @NotNull LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
    if (sdk != null && PythonSdkUtil.isPythonSdk(sdk)) {
      final PythonSdkFlavor<?> flavor = PythonSdkFlavor.getFlavor(sdk);
      if (flavor != null) {
        return flavor.getLanguageLevel(sdk);
      }
      String versionString = sdk.getVersionString();
      if (versionString != null) {
        return LanguageLevel.fromPythonVersion(sdk.getVersionString());
      }
    }
    return LanguageLevel.getDefault();
  }

  /**
   * @return name of builtins skeleton file; for Python 2.x it is '{@code __builtins__.py}'.
   */


  @ApiStatus.Internal
  public static @NotNull @NonNls String getBuiltinsFileName(@NotNull Sdk sdk) {
    return PyBuiltinCache.getBuiltinsFileName(getLanguageLevelForSdk(sdk));
  }

  /**
   * Finds sdk for provided directory. Takes into account both project and module SDK
   *
   * @param allowRemote - indicates whether remote interpreter is acceptable
   */
  @ApiStatus.Internal
  public static @Nullable Sdk findSdkForDirectory(@NotNull Project project, @NotNull Path workingDirectory, boolean allowRemote) {
    VirtualFile workingDirectoryVirtualFile = LocalFileSystem.getInstance().findFileByNioFile(workingDirectory);
    if (workingDirectoryVirtualFile != null) {
      Sdk sdk = getLocalSdkForFile(project, workingDirectoryVirtualFile, allowRemote);
      if (sdk != null) {
        return sdk;
      }
    }

    for (Module m : ModuleManager.getInstance(project).getModules()) {
      Sdk sdk = PythonSdkUtil.findPythonSdk(m);
      if (sdk != null && (allowRemote || !PythonSdkUtil.isRemote(sdk))) {
        return sdk;
      }
    }

    return null;
  }

  private static @Nullable Sdk getLocalSdkForFile(@NotNull Project project, @NotNull VirtualFile workingDirectoryVirtualFile, boolean allowRemote) {
    Module module = ModuleUtilCore.findModuleForFile(workingDirectoryVirtualFile, project);
    if (module != null) {
      Sdk sdk = PythonSdkUtil.findPythonSdk(module);
      if (sdk != null && (allowRemote || !PythonSdkUtil.isRemote(sdk))) {
        return sdk;
      }
    }
    return null;
  }
}
