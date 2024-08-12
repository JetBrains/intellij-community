// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.jetbrains.python.sdk.PySdkExtKt.showSdkExecutionException;

/**
 * @deprecated This class and all its inheritors are deprecated. Everything should work via {@link PyTargetEnvironmentPackageManager}
 */
@Deprecated
public class PyPackageManagerImpl extends PyPackageManagerImplBase {
  private static final String VIRTUALENV_ZIPAPP_NAME = "virtualenv-20.24.5.pyz";
  private static final String LEGACY_VIRTUALENV_ZIPAPP_NAME = "virtualenv-20.13.0.pyz"; // virtualenv used to create virtual environments for python 2.7 & 3.6

  private static final Logger LOG = Logger.getInstance(PyPackageManagerImpl.class);

  @Override
  protected final void installUsingPipWheel(String @NotNull ... pipArgs) throws ExecutionException {
    final String pipWheel = getHelperPath(PIP_WHEEL_NAME);
    List<String> args = Lists.newArrayList(INSTALL);
    args.addAll(Arrays.asList(pipArgs));
    getPythonProcessResult(pipWheel + mySeparator + PyPackageUtil.PIP, args,
                           true, true, null);
  }

  protected @NotNull String toSystemDependentName(final @NotNull String dirName) {
    return FileUtil.toSystemDependentName(dirName);
  }

  protected PyPackageManagerImpl(final @NotNull Sdk sdk) {
    super(sdk);
  }

  @Override
  public void install(@NotNull String requirementString) throws ExecutionException {
    install(Collections.singletonList(parseRequirement(requirementString)), Collections.emptyList());
  }

  @Override
  public void install(@Nullable List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException {
    install(requirements, extraArgs, null);
  }

  public void install(@Nullable List<PyRequirement> requirements, @NotNull List<String> extraArgs, @Nullable String workingDir)
    throws ExecutionException {
    if (requirements == null) return;
    if (!hasManagement()) {
      installManagement();
    }
    final List<String> args = new ArrayList<>();
    args.add(INSTALL);

    final boolean useUserSite = extraArgs.contains(USE_USER_SITE);

    final String proxyString = getProxyString();
    if (proxyString != null) {
      args.add("--proxy");
      args.add(proxyString);
    }
    args.addAll(extraArgs);
    for (PyRequirement req : requirements) {
      args.addAll(req.getInstallOptions());
    }

    try {
      getHelperResult(args, !useUserSite, true, workingDir);
    }
    catch (PyExecutionException e) {
      final List<String> simplifiedArgs = new ArrayList<>();
      simplifiedArgs.add("install");
      if (proxyString != null) {
        simplifiedArgs.add("--proxy");
        simplifiedArgs.add(proxyString);
      }
      simplifiedArgs.addAll(extraArgs);
      for (PyRequirement req : requirements) {
        simplifiedArgs.addAll(req.getInstallOptions());
      }
      throw new PyExecutionException(e.getMessage(), "pip", makeSafeToDisplayCommand(simplifiedArgs),
                                     e.getStdout(), e.getStderr(), e.getExitCode(), e.getFixes());
    }
    finally {
      LOG.debug("Packages cache is about to be refreshed because these requirements were installed: " + requirements);
      refreshPackagesSynchronously();
    }
  }

  @Override
  public void uninstall(@NotNull List<PyPackage> packages) throws ExecutionException {
    final List<String> args = new ArrayList<>();
    try {
      args.add(UNINSTALL);
      boolean canModify = true;
      for (PyPackage pkg : packages) {
        if (canModify) {
          final String location = pkg.getLocation();
          if (location != null) {
            canModify = Files.isWritable(Paths.get(location));
          }
        }
        args.add(pkg.getName());
      }
      getHelperResult(args, !canModify, true);
    }
    catch (PyExecutionException e) {
      throw new PyExecutionException(e.getMessage(), "pip", args, e.getStdout(), e.getStderr(), e.getExitCode(), e.getFixes());
    }
    finally {
      LOG.debug("Packages cache is about to be refreshed because these packages were uninstalled: " + packages);
      refreshPackagesSynchronously();
    }
  }


  @Override
  public @Nullable List<PyPackage> getPackages() {
    final List<PyPackage> packages = myPackagesCache;
    return packages != null ? Collections.unmodifiableList(packages) : null;
  }

  @Override
  protected @NotNull List<PyPackage> collectPackages() throws ExecutionException {
    if (getSdk() instanceof PyLazySdk) {
      return List.of();
    }

    try {
      LOG.debug("Collecting installed packages for the SDK " + getSdk().getName(), new Throwable());
      String output = getHelperResult(List.of("list"), false, false);
      return parsePackagingToolOutput(output);
    }
    catch (ProcessNotCreatedException ex) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.info("Not-env unit test mode, will return mock packages");
        return List.of(new PyPackage(PyPackageUtil.PIP, PIP_VERSION),
                       new PyPackage(PyPackageUtil.SETUPTOOLS, SETUPTOOLS_VERSION));
      }
      else {
        throw ex;
      }
    }
  }

  @Override
  public @NotNull String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws ExecutionException {
    final List<String> args = new ArrayList<>();
    final Sdk sdk = getSdk();
    final LanguageLevel languageLevel = getOrRequestLanguageLevelForSdk(sdk);

    if (languageLevel.isOlderThan(LanguageLevel.PYTHON27)) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.packaging.creating.virtual.environment.for.python.not.supported",
                                                       languageLevel, LanguageLevel.PYTHON27));
    }

    if (useGlobalSite) {
      args.add("--system-site-packages");
    }
    args.add(destinationDir);

    try {
      getPythonProcessResult(
        Objects.requireNonNull(getHelperPath(isLegacyPython(languageLevel) ? LEGACY_VIRTUALENV_ZIPAPP_NAME : VIRTUALENV_ZIPAPP_NAME)),
        args, false, true, null, List.of("-S"));
    }
    catch (ExecutionException e) {
      showSdkExecutionException(sdk, e, PySdkBundle.message("python.creating.venv.failed.title"));
    }

    final Path binary =  VirtualEnvReader.getInstance().findPythonInPythonRoot(Paths.get(destinationDir));
    final String binaryFallback = destinationDir + mySeparator + "bin" + mySeparator + "python";

    return (binary != null) ? binary.toString() : binaryFallback;
  }

  /**
   * Is it a legacy python version that we still support
   */
  private static boolean isLegacyPython(@NotNull LanguageLevel languageLevel) {
    return languageLevel.isPython2() || languageLevel.isOlderThan(LanguageLevel.PYTHON37);
  }

  //   public List<PyPackage> refreshAndGetPackagesIfNotInProgress(boolean alwaysRefresh) throws ExecutionException

  private @NotNull String getHelperResult(@NotNull List<String> args,
                                          boolean askForSudo,
                                          boolean showProgress) throws ExecutionException {
    return getHelperResult(args, askForSudo, showProgress, null);
  }

  private @NotNull String getHelperResult(@NotNull List<String> args,
                                          boolean askForSudo,
                                          boolean showProgress,
                                          @Nullable String parentDir) throws ExecutionException {
    String helperPath = getHelperPath(PACKAGING_TOOL);
    if (helperPath == null) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.packaging.cannot.find.external.tool", PACKAGING_TOOL));
    }
    return getPythonProcessResult(helperPath, args, askForSudo, showProgress, parentDir);
  }

  private @NotNull String getPythonProcessResult(@NotNull String path, @NotNull List<String> args, boolean askForSudo,
                                                 boolean showProgress, @Nullable String workingDir) throws ExecutionException {
    return getPythonProcessResult(path, args, askForSudo, showProgress, workingDir, null);
  }

  private @NotNull String getPythonProcessResult(@NotNull String path, @NotNull List<String> args, boolean askForSudo,
                                                 boolean showProgress, @Nullable String workingDir, @Nullable List<String> pyArgs)
    throws ExecutionException {
    final ProcessOutput output = getPythonProcessOutput(path, args, askForSudo, showProgress, workingDir, pyArgs);
    final int exitCode = output.getExitCode();
    if (output.isTimeout()) {
      throw new PyExecutionException(PySdkBundle.message("python.sdk.packaging.timed.out"), path, args, output);
    }
    else if (exitCode != 0) {
      throw new PyExecutionException(PySdkBundle.message("python.sdk.packaging.non.zero.exit.code", exitCode), path, args, output);
    }
    return output.getStdout();
  }

  protected @NotNull ProcessOutput getPythonProcessOutput(@NotNull String helperPath, @NotNull List<String> args, boolean askForSudo,
                                                          boolean showProgress, @Nullable String workingDir, @Nullable List<String> pyArgs)
    throws ExecutionException {
    final String homePath = getSdk().getHomePath();
    if (homePath == null) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.packaging.cannot.find.python.interpreter", getSdk().getName()));
    }
    if (workingDir == null) {
      workingDir = new File(homePath).getParent();
    }
    final List<String> cmdline = new ArrayList<>();
    cmdline.add(homePath);
    if (pyArgs != null) cmdline.addAll(pyArgs);
    cmdline.add(helperPath);
    cmdline.addAll(args);
    LOG.info("Running packaging tool: " + StringUtil.join(makeSafeToDisplayCommand(cmdline), " "));

    try {
      final GeneralCommandLine commandLine =
        new GeneralCommandLine(cmdline).withWorkDirectory(workingDir).withEnvironment(PySdkUtil.activateVirtualEnv(getSdk()));
      final Map<String, String> environment = commandLine.getEnvironment();
      PythonEnvUtil.setPythonUnbuffered(environment);
      PythonEnvUtil.setPythonDontWriteBytecode(environment);
      PythonEnvUtil.resetHomePathChanges(homePath, environment);
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(getSdk());
      if (flavor != null && flavor.commandLinePatcher() != null) {
        flavor.commandLinePatcher().patchCommandLine(commandLine);
      }
      final Process process;
      final boolean useSudo = askForSudo && PySdkExtKt.adminPermissionsNeeded(getSdk());
      if (useSudo) {
        process = ExecUtil.sudo(commandLine, PySdkBundle.message("python.sdk.packaging.enter.your.password.to.make.changes"));
      }
      else {
        process = commandLine.createProcess();
      }
      final CapturingProcessHandler handler =
        new CapturingProcessHandler(process, commandLine.getCharset(), commandLine.getCommandLineString());
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      final ProcessOutput result;
      if (showProgress && indicator != null) {
        handler.addProcessListener(new IndicatedProcessOutputListener(indicator));
        result = handler.runProcessWithProgressIndicator(indicator);
      }
      else {
        result = handler.runProcess(TIMEOUT);
      }
      if (result.isCancelled()) {
        throw new RunCanceledByUserException();
      }
      result.checkSuccess(LOG);
      final int exitCode = result.getExitCode();
      if (exitCode != 0) {
        final String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr())
                               ? PySdkBundle.message("python.conda.permission.denied")
                               : PySdkBundle.message("python.sdk.packaging.non.zero.exit.code", exitCode);
        throw new PyExecutionException(message, helperPath, args, result);
      }
      return result;
    }
    catch (IOException e) {
      throw new PyExecutionException(e.getMessage(), helperPath, args);
    }
  }
}
