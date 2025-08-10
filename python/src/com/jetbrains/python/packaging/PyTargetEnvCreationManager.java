// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetProgressIndicator;
import com.intellij.execution.target.TargetedCommandLine;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.value.TargetEnvironmentFunctions;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.python.community.helpersLocator.PythonHelpersLocator;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.packaging.common.PythonPackage;
import com.jetbrains.python.packaging.pip.PipParseUtils;
import com.jetbrains.python.packaging.repository.PyPackageRepositoryUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.PythonExecution;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.run.PythonScriptExecution;
import com.jetbrains.python.run.PythonScripts;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.venvReader.VirtualEnvReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @deprecated TODO: explain
 */
@SuppressWarnings("ALL")
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public class PyTargetEnvCreationManager {
  private static final Logger LOG = Logger.getInstance(PyTargetEnvCreationManager.class);
  private final @NotNull Sdk mySdk;

  protected static final String SETUPTOOLS_VERSION = "44.1.1";
  protected static final String PIP_VERSION = "24.3.1";

  protected static final String SETUPTOOLS_WHEEL_NAME = "setuptools-" + SETUPTOOLS_VERSION + "-py2.py3-none-any.whl";
  protected static final String PIP_WHEEL_NAME = "pip-" + PIP_VERSION + "-py2.py3-none-any.whl";

  protected static final int ERROR_NO_SETUPTOOLS = 3;


  protected static final String PACKAGING_TOOL = "packaging_tool.py";
  protected static final int TIMEOUT = 10 * 60 * 1000;

  protected static final String INSTALL = "install";
  protected static final String UNINSTALL = "uninstall";
  private final AtomicBoolean myUpdatingCache = new AtomicBoolean(false);
  protected String mySeparator = File.separator;
  protected volatile @Nullable List<PyPackage> myPackagesCache = null;

  public PyTargetEnvCreationManager(final @NotNull Sdk sdk) {
    mySdk = sdk;
  }

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

  private @NotNull HelpersAwareTargetEnvironmentRequest getPythonTargetInterpreter() throws ExecutionException {
    HelpersAwareTargetEnvironmentRequest request = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(getSdk(),
                                                                                                                         ProjectManager.getInstance()
                                                                                                                           .getDefaultProject());
    if (request == null) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.package.managing.not.supported.for.sdk", getSdk().getName()));
    }
    return request;
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
      throw PyExecutionException.createForTimeout(PySdkBundle.message("python.sdk.packaging.timed.out"), path, args);
    }
    else if (exitCode != 0) {
      throw new PyExecutionException(PySdkBundle.message("python.sdk.packaging.non.zero.exit.code", exitCode), path, args, processOutput);
    }
    return processOutput.getStdout();
  }

  protected final @NotNull Sdk getSdk() {
    return mySdk;
  }


  private @NotNull PyTargetEnvCreationManager.ProcessOutputWithCommandLine getPythonProcessOutput(@NotNull PythonExecution pythonExecution,
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

  protected void installUsingPipWheel(String @NotNull ... pipArgs) throws ExecutionException {
    HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest = getPythonTargetInterpreter();
    PythonScriptExecution pythonExecution =
      PythonScripts.prepareHelperScriptExecution(getPipHelperPackage(), helpersAwareTargetRequest);
    pythonExecution.addParameter(INSTALL);
    pythonExecution.addParameters(pipArgs);

    getPythonProcessResult(pythonExecution, true, true, helpersAwareTargetRequest.getTargetEnvironmentRequest());
  }

  @RequiresReadLock(generateAssertion = false)
  protected static @NotNull LanguageLevel getOrRequestLanguageLevelForSdk(@NotNull Sdk sdk) throws ExecutionException {
    if (sdk instanceof PyDetectedSdk) {
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
      if (flavor != null && sdk.getHomePath() != null) {
        return flavor.getLanguageLevel(sdk.getHomePath());
      }
      throw new ExecutionException(PySdkBundle.message("python.sdk.packaging.cannot.retrieve.version", sdk.getHomePath()));
    }
    // Use the cached version for an already configured SDK
    return PythonSdkType.getLanguageLevelForSdk(sdk);
  }

  protected static @Nullable String getProxyString() {
    final HttpConfigurable settings = HttpConfigurable.getInstance();
    if (settings != null && settings.USE_HTTP_PROXY) {
      final String credentials;
      if (settings.PROXY_AUTHENTICATION) {
        credentials = String.format("%s:%s@", settings.getProxyLogin(), settings.getPlainProxyPassword());
      }
      else {
        credentials = "";
      }
      return "http://" + credentials + String.format("%s:%d", settings.PROXY_HOST, settings.PROXY_PORT);
    }
    return null;
  }

  protected static @NotNull List<String> makeSafeToDisplayCommand(@NotNull List<String> cmdline) {
    final List<String> safeCommand = new ArrayList<>(cmdline);
    for (int i = 0; i < safeCommand.size(); i++) {
      if (cmdline.get(i).equals("--proxy") && i + 1 < cmdline.size()) {
        safeCommand.set(i + 1, makeSafeUrlArgument(cmdline.get(i + 1)));
      }
      if (cmdline.get(i).equals("--index-url") && i + 1 < cmdline.size()) {
        safeCommand.set(i + 1, makeSafeUrlArgument(cmdline.get(i + 1)));
      }
    }
    return safeCommand;
  }

  private static @NotNull String makeSafeUrlArgument(@NotNull String urlArgument) {
    try {
      final URI proxyUri = new URI(urlArgument);
      final String credentials = proxyUri.getUserInfo();
      if (credentials != null) {
        final int colonIndex = credentials.indexOf(":");
        if (colonIndex >= 0) {
          final String login = credentials.substring(0, colonIndex);
          final String password = credentials.substring(colonIndex + 1);
          final String maskedPassword = StringUtil.repeatSymbol('*', password.length());
          final String maskedCredentials = login + ":" + maskedPassword;
          if (urlArgument.contains(credentials)) {
            return urlArgument.replaceFirst(Pattern.quote(credentials), maskedCredentials);
          }
          else {
            final String encodedCredentials = PyPackageRepositoryUtil.encodeCredentialsForUrl(login, password);
            return urlArgument.replaceFirst(Pattern.quote(encodedCredentials), maskedCredentials);
          }
        }
      }
    }
    catch (URISyntaxException ignored) {
    }
    return urlArgument;
  }

  protected static @NotNull List<PyPackage> parsePackagingToolOutput(@NotNull String output) {
    List<@NotNull PythonPackage> packageList = PipParseUtils.parseListResult(output);
    List<PyPackage> packages = new ArrayList<>();
    for (PythonPackage pythonPackage : packageList) {
      PyPackage pkg = new PyPackage(pythonPackage.getName(), pythonPackage.getVersion());
      packages.add(pkg);
    }
    return packages;
  }

  private static void applyWorkingDir(@NotNull PythonScriptExecution execution, @Nullable String workingDir) {
    if (workingDir == null) {
      // TODO [targets] Set the parent of home path as the working directory
    }
    else {
      execution.setWorkingDir(TargetEnvironmentFunctions.constant(workingDir));
    }
  }


  private static @NotNull Process executeOnLocalMachineWithSudo(@NotNull GeneralCommandLine localCommandLine) throws ExecutionException {
    try {
      return ExecUtil.sudo(localCommandLine, PySdkBundle.message("python.sdk.packaging.enter.your.password.to.make.changes"));
    }
    catch (IOException e) {
      String exePath = localCommandLine.getExePath();
      List<String> args = localCommandLine.getCommandLineList(exePath);
      throw new PyExecutionException(e, null, exePath, args);
    }
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
