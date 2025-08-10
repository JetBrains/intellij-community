// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.community.helpersLocator.PythonHelpersLocator;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.jetbrains.python.HelperPackage;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.errorProcessing.ExecErrorImpl;
import com.jetbrains.python.errorProcessing.ExecErrorReason;
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

import static com.intellij.webcore.packaging.PackageVersionComparator.VERSION_COMPARATOR;

/**
 * @deprecated TODO: explain
 */
@SuppressWarnings("ALL")
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public abstract class PyTargetEnvironmentPackageManager extends PyPackageManager {
  private static final Logger LOG = Logger.getInstance(PyTargetEnvironmentPackageManager.class);

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

  @Override
  public void refresh() {
    LOG.debug("Refreshing SDK roots and packages cache");
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> {
      final Sdk sdk = getSdk();
      application.runWriteAction(() -> {
        final VirtualFile[] files = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
        VfsUtil.markDirtyAndRefresh(true, true, true, files);
      });
      PythonSdkType.getInstance().setupSdkPaths(sdk);
    });
  }

  @Override
  public void installManagement() throws ExecutionException {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(getSdk());
    if (languageLevel.isOlderThan(LanguageLevel.PYTHON27)) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.packaging.package.management.for.python.not.supported",
                                                       languageLevel, LanguageLevel.PYTHON27));
    }

    boolean success = updatePackagingTools();
    if (success) {
      return;
    }

    if (languageLevel.isOlderThan(LanguageLevel.PYTHON312)) { // Python 3.12 doesn't require setuptools to list packages anymore
      final PyPackage installedSetuptools = refreshAndCheckForSetuptools();
      if (installedSetuptools == null || VERSION_COMPARATOR.compare(installedSetuptools.getVersion(), SETUPTOOLS_VERSION) < 0) {
        installManagement(Objects.requireNonNull(getHelperPath(SETUPTOOLS_WHEEL_NAME)));
      }
    }

    final PyPackage installedPip = PyPsiPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP);
    if (installedPip == null || VERSION_COMPARATOR.compare(installedPip.getVersion(), PIP_VERSION) < 0) {
      installManagement(Objects.requireNonNull(getHelperPath(PIP_WHEEL_NAME)));
    }
  }

  protected final boolean updatePackagingTools() {
    try {
      installUsingPipWheel("--upgrade", "--force-reinstall", PyPackageUtil.SETUPTOOLS, PyPackageUtil.PIP);
      return true;
    }
    catch (ExecutionException e) {
      LOG.info(e);
      return false;
    }
    finally {
      refreshPackagesSynchronously();
    }
  }

  @Override
  public boolean hasManagement() throws ExecutionException {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(getSdk());
    final Boolean hasSetuptools = languageLevel.isAtLeast(LanguageLevel.PYTHON312) || refreshAndCheckForSetuptools() != null;
    final Boolean hasPip = PyPsiPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP) != null;
    return hasSetuptools && hasPip;
  }

  protected final @Nullable PyPackage refreshAndCheckForSetuptools() throws ExecutionException {
    try {
      final List<PyPackage> packages = refreshAndGetPackages(false);
      final PyPackage setuptoolsPackage = PyPsiPackageUtil.findPackage(packages, PyPackageUtil.SETUPTOOLS);
      return setuptoolsPackage != null ? setuptoolsPackage : PyPsiPackageUtil.findPackage(packages, PyPackageUtil.DISTRIBUTE);
    }
    catch (PyExecutionException e) {
      var pyError = e.getPyError();
      if (pyError instanceof ExecErrorImpl<?> error) {
        var errorReason = error.getErrorReason();
        if (errorReason instanceof ExecErrorReason.UnexpectedProcessTermination unexpectedProcessTermination) {
          int exitCode = unexpectedProcessTermination.getExitCode();
          if (exitCode == ERROR_NO_SETUPTOOLS) {
            return null;
          }
        }
      }
      throw e;
    }
  }

  protected void installManagement(@NotNull String name) throws ExecutionException {
    installUsingPipWheel("--no-index", name);
  }

  @Override
  public @NotNull List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException {
    final List<PyPackage> currentPackages = myPackagesCache;
    if (alwaysRefresh || currentPackages == null) {
      myPackagesCache = null;
      try {
        final List<PyPackage> packages = collectPackages();
        LOG.debug("Packages installed in " + getSdk().getName() + ": " + packages);
        myPackagesCache = packages;
        ApplicationManager.getApplication().getMessageBus().syncPublisher(PACKAGE_MANAGER_TOPIC).packagesRefreshed(getSdk());
        return Collections.unmodifiableList(packages);
      }
      catch (ExecutionException e) {
        myPackagesCache = Collections.emptyList();
        throw e;
      }
    }
    return Collections.unmodifiableList(currentPackages);
  }

  protected final void refreshPackagesSynchronously() {
    PyPackageUtil.updatePackagesSynchronouslyWithGuard(this, myUpdatingCache);
  }

  protected @Nullable String getHelperPath(final @NotNull String helper) throws ExecutionException {
    return PythonHelpersLocator.findPathStringInHelpers(helper);
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

  protected void installUsingPipWheel(String @NotNull ... pipArgs) throws ExecutionException {
    HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest = getPythonTargetInterpreter();
    PythonScriptExecution pythonExecution =
      PythonScripts.prepareHelperScriptExecution(getPipHelperPackage(), helpersAwareTargetRequest);
    pythonExecution.addParameter(INSTALL);
    pythonExecution.addParameters(pipArgs);

    getPythonProcessResult(pythonExecution, true, true, helpersAwareTargetRequest.getTargetEnvironmentRequest());
  }

  public PyTargetEnvironmentPackageManager(final @NotNull Sdk sdk) {
    super(sdk);
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
      throw PyExecutionExceptionExtKt.copyWith(e, "pip", makeSafeToDisplayCommand(simplifiedArgs));
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
  public @Nullable List<PyPackage> getPackages() {
    final List<PyPackage> packages = myPackagesCache;
    return packages != null ? Collections.unmodifiableList(packages) : null;
  }

  protected @NotNull List<PyPackage> collectPackages() throws ExecutionException {
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
      throw PyExecutionException.createForTimeout(PySdkBundle.message("python.sdk.packaging.timed.out"), path, args);
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
      throw new PyExecutionException(e, null, exePath, args);
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
