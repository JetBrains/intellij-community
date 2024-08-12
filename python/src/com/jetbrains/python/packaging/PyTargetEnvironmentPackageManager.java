// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetProgressIndicator;
import com.intellij.execution.target.TargetedCommandLine;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.PythonExecution;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.run.PythonScriptExecution;
import com.jetbrains.python.run.PythonScripts;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.sdk.PyLazySdk;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.VirtualEnvReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PyTargetEnvironmentPackageManager extends PyPackageManagerImplBase {
  private static final Logger LOG = Logger.getInstance(PyTargetEnvironmentPackageManager.class);

  @Override
  protected void installUsingPipWheel(String @NotNull ... pipArgs) throws ExecutionException {
    HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest = getPythonTargetInterpreter();
    PythonScriptExecution pythonExecution =
      PythonScripts.prepareHelperScriptExecution(getPipHelperPackage(), helpersAwareTargetRequest);
    pythonExecution.addParameter(INSTALL);
    pythonExecution.addParameters(pipArgs);

    getPythonProcessResult(pythonExecution, true, true, helpersAwareTargetRequest.getTargetEnvironmentRequest());
  }

  PyTargetEnvironmentPackageManager(final @NotNull Sdk sdk) {
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
    HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest = getPythonTargetInterpreter();
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareTargetRequest.getTargetEnvironmentRequest();
    PythonScriptExecution pythonExecution =
      PythonScripts.prepareHelperScriptExecution(PythonHelper.PACKAGING_TOOL, helpersAwareTargetRequest);

    applyWorkingDir(pythonExecution, workingDir);

    pythonExecution.addParameter(INSTALL);

    final boolean useUserSite = extraArgs.contains(USE_USER_SITE);

    final String proxyString = getProxyString();
    if (proxyString != null) {
      pythonExecution.addParameter("--proxy");
      pythonExecution.addParameter(proxyString);
    }
    pythonExecution.addParameters(extraArgs);
    for (PyRequirement req : requirements) {
      pythonExecution.addParameters(req.getInstallOptions());
    }
    try {
      getPythonProcessResult(pythonExecution, !useUserSite, true, targetEnvironmentRequest);
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

  private static void applyWorkingDir(@NotNull PythonScriptExecution execution, @Nullable String workingDir) {
    if (workingDir == null) {
      // TODO [targets] Set the parent of home path as the working directory
    }
    else {
      execution.setWorkingDir(TargetEnvironmentFunctions.constant(workingDir));
    }
  }

  @Override
  public void uninstall(@NotNull List<PyPackage> packages) throws ExecutionException {
    List<String> args = new ArrayList<>();
    HelpersAwareTargetEnvironmentRequest helpersAwareRequest = getPythonTargetInterpreter();
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareRequest.getTargetEnvironmentRequest();
    PythonScriptExecution pythonExecution =
      PythonScripts.prepareHelperScriptExecution(PythonHelper.PACKAGING_TOOL, helpersAwareRequest);
    try {
      pythonExecution.addParameter(UNINSTALL);
      // TODO [targets] Remove temporary usage of String arguments
      args.add(UNINSTALL);
      boolean canModify = true;
      for (PyPackage pkg : packages) {
        if (canModify) {
          final String location = pkg.getLocation();
          if (location != null) {
            // TODO [targets] Introspection is required here
            canModify = Files.isWritable(Paths.get(location));
          }
        }
        pythonExecution.addParameter(pkg.getName());
        // TODO [targets] Remove temporary usage of String arguments
        args.add(pkg.getName());
      }
      // TODO [targets] Pass `parentDir = null`
      getPythonProcessResult(pythonExecution, !canModify, true, targetEnvironmentRequest);
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
    if (!Registry.is("python.use.targets.api")) {
      return Collections.emptyList();
    }
    final List<PyPackage> packages = myPackagesCache;
    return packages != null ? Collections.unmodifiableList(packages) : null;
  }

  @Override
  protected @NotNull List<PyPackage> collectPackages() throws ExecutionException {
    assertUseTargetsAPIFlagEnabled();
    if (getSdk() instanceof PyLazySdk) {
      return List.of();
    }

    HelpersAwareTargetEnvironmentRequest helpersAwareRequest = getPythonTargetInterpreter();
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareRequest.getTargetEnvironmentRequest();
    final String output;
    try {
      LOG.debug("Collecting installed packages for the SDK " + getSdk().getName(), new Throwable());
      PythonScriptExecution pythonExecution =
        PythonScripts.prepareHelperScriptExecution(PythonHelper.PACKAGING_TOOL, helpersAwareRequest);
      pythonExecution.addParameter("list");
      output = getPythonProcessResult(pythonExecution, false, false, targetEnvironmentRequest);
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

    return parsePackagingToolOutput(output);
  }

  private static void assertUseTargetsAPIFlagEnabled() throws ExecutionException {
    if (!Registry.is("python.use.targets.api")) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.please.reconfigure.interpreter"));
    }
  }

  @Override
  public @NotNull String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws ExecutionException {
    final Sdk sdk = getSdk();
    final LanguageLevel languageLevel = getOrRequestLanguageLevelForSdk(sdk);

    if (languageLevel.isOlderThan(LanguageLevel.PYTHON27)) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.packaging.creating.virtual.environment.for.python.not.supported",
                                                       languageLevel, LanguageLevel.PYTHON27));
    }

    HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest = getPythonTargetInterpreter();
    TargetEnvironmentRequest targetEnvironmentRequest = helpersAwareTargetRequest.getTargetEnvironmentRequest();

    PythonScriptExecution pythonExecution = PythonScripts.prepareHelperScriptExecution(
      isLegacyPython(languageLevel) ? PythonHelper.LEGACY_VIRTUALENV_ZIPAPP : PythonHelper.VIRTUALENV_ZIPAPP,
      helpersAwareTargetRequest);
    if (useGlobalSite) {
      pythonExecution.addParameter("--system-site-packages");
    }
    pythonExecution.addParameter(destinationDir);
    // TODO [targets] Pass `parentDir = null`
    getPythonProcessResult(pythonExecution, false, true, targetEnvironmentRequest);

    final Path binary = VirtualEnvReader.getInstance().findPythonInPythonRoot(Path.of(destinationDir));
    final char separator = targetEnvironmentRequest.getTargetPlatform().getPlatform().fileSeparator;
    final String binaryFallback = destinationDir + separator + "bin" + separator + "python";

    return (binary != null) ? binary.toString() : binaryFallback;
  }

  /**
   * Is it a legacy python version that we still support
   */
  private static @NotNull Boolean isLegacyPython(@NotNull LanguageLevel languageLevel) {
    return languageLevel.isPython2() || languageLevel.isOlderThan(LanguageLevel.PYTHON37);
  }

  private @NotNull String getPythonProcessResult(@NotNull PythonExecution pythonExecution,
                                                 boolean askForSudo,
                                                 boolean showProgress,
                                                 @NotNull TargetEnvironmentRequest targetEnvironmentRequest) throws ExecutionException {
    ProcessOutputWithCommandLine result = getPythonProcessOutput(pythonExecution, askForSudo, showProgress, targetEnvironmentRequest);
    String path = result.getExePath();
    List<String> args = result.getArgs();
    ProcessOutput processOutput = result.getProcessOutput();
    int exitCode = processOutput.getExitCode();
    if (processOutput.isTimeout()) {
      // TODO [targets] Make cancellable right away?
      throw new PyExecutionException(PySdkBundle.message("python.sdk.packaging.timed.out"), path, args, processOutput);
    }
    else if (exitCode != 0) {
      throw new PyExecutionException(PySdkBundle.message("python.sdk.packaging.non.zero.exit.code", exitCode), path, args, processOutput);
    }
    return processOutput.getStdout();
  }

  private @NotNull PyTargetEnvironmentPackageManager.ProcessOutputWithCommandLine getPythonProcessOutput(@NotNull PythonExecution pythonExecution,
                                                                                                         boolean askForSudo,
                                                                                                         boolean showProgress,
                                                                                                         @NotNull TargetEnvironmentRequest targetEnvironmentRequest)
    throws ExecutionException {
    // TODO [targets] Use `showProgress = true`
    // TODO [targets] Use `workingDir`
    // TODO [targets] Use `useUserSite` (handle use sudo)
    TargetProgressIndicator targetProgressIndicator = TargetProgressIndicator.EMPTY;
    TargetEnvironment targetEnvironment = targetEnvironmentRequest.prepareEnvironment(targetProgressIndicator);
    for (Map.Entry<TargetEnvironment.UploadRoot, TargetEnvironment.UploadableVolume> entry : targetEnvironment.getUploadVolumes()
      .entrySet()) {
      try {
        entry.getValue().upload(".", TargetProgressIndicator.EMPTY);
      }
      catch (IOException e) {
        throw new ExecutionException(e);
      }
    }
    // TODO [targets] Should `interpreterParameters` be here?
    TargetedCommandLine targetedCommandLine = PythonScripts.buildTargetedCommandLine(pythonExecution,
                                                                                     targetEnvironment,
                                                                                     getSdk(),
                                                                                     Collections.emptyList()
    );
    // TODO [targets] Set parent directory of interpreter as the working directory

    LOG.info("Running packaging tool");

    // TODO [targets] Apply environment variables: setPythonUnbuffered(...), setPythonDontWriteBytecode(...), resetHomePathChanges(...)
    // TODO [targets] Apply flavor from PythonSdkFlavor.getFlavor(mySdk)
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    Process process = createProcess(targetEnvironment, targetedCommandLine, askForSudo, indicator);
    List<String> commandLine = targetedCommandLine.collectCommandsSynchronously();
    String commandLineString = StringUtil.join(commandLine, " ");
    final CapturingProcessHandler handler =
      new CapturingProcessHandler(process, targetedCommandLine.getCharset(), commandLineString);
    final ProcessOutput result;
    if (showProgress && indicator != null) {
      handler.addProcessListener(new IndicatedProcessOutputListener(indicator));
      result = handler.runProcessWithProgressIndicator(indicator);
    }
    else {
      // TODO [targets] Check if timeout is ok for all targets
      result = handler.runProcess(TIMEOUT);
    }
    if (result.isCancelled()) {
      throw new RunCanceledByUserException();
    }
    result.checkSuccess(LOG);
    final int exitCode = result.getExitCode();
    String helperPath = ContainerUtil.getFirstItem(commandLine, "");
    List<String> args = commandLine.subList(Math.min(1, commandLine.size()), commandLine.size());
    if (exitCode != 0) {
      final String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr())
                             ? PySdkBundle.message("python.conda.permission.denied")
                             : PySdkBundle.message("python.sdk.packaging.non.zero.exit.code", exitCode);
      throw new PyExecutionException(message, helperPath, args, result);
    }
    return new ProcessOutputWithCommandLine(helperPath, args, result);
  }

  private @NotNull Process createProcess(@NotNull TargetEnvironment targetEnvironment,
                                         @NotNull TargetedCommandLine targetedCommandLine,
                                         boolean askForSudo,
                                         @Nullable ProgressIndicator indicator) throws ExecutionException {
    if (askForSudo) {
      if (!(targetEnvironment instanceof LocalTargetEnvironment)) {
        // TODO [targets] Execute process on non-local target using sudo
        LOG.warn("Sudo flag is ignored");
      }
      else if (PySdkExtKt.adminPermissionsNeeded(getSdk())) {
        // This is hack to process sudo flag in the local environment
        GeneralCommandLine localCommandLine = ((LocalTargetEnvironment)targetEnvironment).createGeneralCommandLine(targetedCommandLine);
        return executeOnLocalMachineWithSudo(localCommandLine);
      }
    }
    // TODO [targets] Pass meaningful progress indicator
    return targetEnvironment.createProcess(targetedCommandLine, Objects.requireNonNullElseGet(indicator, EmptyProgressIndicator::new));
  }

  private static @NotNull Process executeOnLocalMachineWithSudo(@NotNull GeneralCommandLine localCommandLine) throws ExecutionException {
    try {
      return ExecUtil.sudo(localCommandLine, PySdkBundle.message("python.sdk.packaging.enter.your.password.to.make.changes"));
    }
    catch (IOException e) {
      String exePath = localCommandLine.getExePath();
      List<String> args = localCommandLine.getCommandLineList(exePath);
      throw new PyExecutionException(e.getMessage(), exePath, args);
    }
  }

  private @NotNull HelpersAwareTargetEnvironmentRequest getPythonTargetInterpreter() throws ExecutionException {
    HelpersAwareTargetEnvironmentRequest request = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(getSdk(),
                                                                                                                         ProjectManager.getInstance()
                                                                                                                           .getDefaultProject());
    if (request == null) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.package.managing.not.supported.for.sdk", getSdk().getName()));
    }
    return request;
  }

  private static @NotNull HelperPackage getPipHelperPackage() {
    return new PythonHelper.ScriptPythonHelper(PIP_WHEEL_NAME + "/" + PyPackageUtil.PIP,
                                               PythonHelpersLocator.getCommunityHelpersRoot().toFile(),
                                               Collections.emptyList());
  }

  private static class ProcessOutputWithCommandLine {
    private final @NotNull String myExePath;
    private final @NotNull List<String> myArgs;
    private final @NotNull ProcessOutput myProcessOutput;

    private ProcessOutputWithCommandLine(@NotNull String exePath,
                                         @NotNull List<String> args,
                                         @NotNull ProcessOutput output) {
      myExePath = exePath;
      myArgs = args;
      myProcessOutput = output;
    }

    private @NotNull String getExePath() { return myExePath; }

    private @NotNull List<String> getArgs() { return myArgs; }

    private @NotNull ProcessOutput getProcessOutput() { return myProcessOutput; }
  }
}
