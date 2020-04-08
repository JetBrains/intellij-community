// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.HttpConfigurable;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.intellij.webcore.packaging.PackageVersionComparator.VERSION_COMPARATOR;

/**
 * @author vlan
 */
public class PyPackageManagerImpl extends PyPackageManager {

  private static final String SETUPTOOLS_VERSION = "44.1.0";
  private static final String PIP_VERSION = "20.0.2";

  private static final String SETUPTOOLS_WHEEL_NAME = "setuptools-" + SETUPTOOLS_VERSION + "-py2.py3-none-any.whl";
  private static final String PIP_WHEEL_NAME = "pip-" + PIP_VERSION + "-py2.py3-none-any.whl";
  private static final String VIRTUALENV_WHEEL_NAME = "virtualenv-16.7.10-py2.py3-none-any.whl";

  private static final int ERROR_NO_SETUPTOOLS = 3;

  private static final Logger LOG = Logger.getInstance(PyPackageManagerImpl.class);

  private static final String PACKAGING_TOOL = "packaging_tool.py";
  private static final int TIMEOUT = 10 * 60 * 1000;

  private static final String BUILD_DIR_OPTION = "--build-dir";

  private static final String INSTALL = "install";
  private static final String UNINSTALL = "uninstall";
  protected String mySeparator = File.separator;

  @Nullable private volatile List<PyPackage> myPackagesCache = null;
  private final AtomicBoolean myUpdatingCache = new AtomicBoolean(false);

  @NotNull final private Sdk mySdk;

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
      throw new ExecutionException("Package management for Python " + languageLevel + " is not supported. " +
                                   "Upgrade your project interpreter to Python " + LanguageLevel.PYTHON27 + " or newer");
    }

    boolean success = updatePackagingTools();
    if (success) {
      return;
    }

    final PyPackage installedSetuptools = refreshAndCheckForSetuptools();
    final PyPackage installedPip = PyPsiPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP);
    if (installedSetuptools == null || VERSION_COMPARATOR.compare(installedSetuptools.getVersion(), SETUPTOOLS_VERSION) < 0) {
      installManagement(SETUPTOOLS_WHEEL_NAME);
    }
    if (installedPip == null || VERSION_COMPARATOR.compare(installedPip.getVersion(), PIP_VERSION) < 0) {
      installManagement(PIP_WHEEL_NAME);
    }
  }

  private boolean updatePackagingTools() {
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
    return refreshAndCheckForSetuptools() != null &&
           PyPsiPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP) != null;
  }

  @Nullable
  private PyPackage refreshAndCheckForSetuptools() throws ExecutionException {
    try {
      final List<PyPackage> packages = refreshAndGetPackages(false);
      final PyPackage setuptoolsPackage = PyPsiPackageUtil.findPackage(packages, PyPackageUtil.SETUPTOOLS);
      return setuptoolsPackage != null ? setuptoolsPackage : PyPsiPackageUtil.findPackage(packages, PyPackageUtil.DISTRIBUTE);
    }
    catch (PyExecutionException e) {
      if (e.getExitCode() == ERROR_NO_SETUPTOOLS) {
        return null;
      }
      throw e;
    }
  }

  protected void installManagement(@NotNull String name) throws ExecutionException {
    installUsingPipWheel("--no-index", name);
  }

  private void installUsingPipWheel(String @NotNull ... pipArgs) throws ExecutionException {
    final String pipWheel = getHelperPath(PIP_WHEEL_NAME);
    List<String> args = Lists.newArrayList(INSTALL);
    args.addAll(Arrays.asList(pipArgs));
    getPythonProcessResult(pipWheel + mySeparator + PyPackageUtil.PIP, args,
                           true, true,
                           PythonHelpersLocator.getHelpersRoot().getAbsolutePath());
  }

  @NotNull
  protected String toSystemDependentName(@NotNull final String dirName) {
    return FileUtil.toSystemDependentName(dirName);
  }

  protected PyPackageManagerImpl(@NotNull final Sdk sdk) {
    mySdk = sdk;
    subscribeToLocalChanges();
  }

  protected void subscribeToLocalChanges() {
    PyPackageUtil.runOnChangeUnderInterpreterPaths(getSdk(), () -> PythonSdkType.getInstance().setupSdkPaths(getSdk()));
  }

  @NotNull
  public Sdk getSdk() {
    return mySdk;
  }

  @Override
  public void install(@NotNull String requirementString) throws ExecutionException {
    install(Collections.singletonList(parseRequirement(requirementString)), Collections.emptyList());
  }

  @Override
  public void install(@Nullable List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException {
    if (requirements == null) return;
    if (!hasManagement()) {
      installManagement();
    }
    final List<String> args = new ArrayList<>();
    args.add(INSTALL);
    final File buildDir;
    try {
      buildDir = FileUtil.createTempDirectory("pycharm-packaging", null);
    }
    catch (IOException e) {
      throw new ExecutionException("Cannot create temporary build directory");
    }
    if (!extraArgs.contains(BUILD_DIR_OPTION)) {
      args.addAll(Arrays.asList(BUILD_DIR_OPTION, buildDir.getAbsolutePath()));
    }

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
      getHelperResult(PACKAGING_TOOL, args, !useUserSite, true, null);
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
      FileUtil.delete(buildDir);
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
      getHelperResult(PACKAGING_TOOL, args, !canModify, true, null);
    }
    catch (PyExecutionException e) {
      throw new PyExecutionException(e.getMessage(), "pip", args, e.getStdout(), e.getStderr(), e.getExitCode(), e.getFixes());
    }
    finally {
      LOG.debug("Packages cache is about to be refreshed because these packages were uninstalled: " + packages);
      refreshPackagesSynchronously();
    }
  }


  @Nullable
  @Override
  public List<PyPackage> getPackages() {
    final List<PyPackage> packages = myPackagesCache;
    return packages != null ? Collections.unmodifiableList(packages) : null;
  }

  @NotNull
  protected List<PyPackage> collectPackages() throws ExecutionException {
    if (mySdk instanceof PyLazySdk) return Collections.emptyList();
    final String output;
    try {
      LOG.debug("Collecting installed packages for the SDK " + mySdk.getName(), new Throwable());
      output = getHelperResult(PACKAGING_TOOL, Collections.singletonList("list"), false, false, null);
    }
    catch (final ProcessNotCreatedException ex) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.info("Not-env unit test mode, will return mock packages");
        return Lists.newArrayList(new PyPackage(PyPackageUtil.PIP, PIP_VERSION, null, Collections.emptyList()),
                                  new PyPackage(PyPackageUtil.SETUPTOOLS, SETUPTOOLS_VERSION, null, Collections.emptyList()));
      }
      else {
        throw ex;
      }
    }

    return parsePackagingToolOutput(output);
  }

  @Override
  @NotNull
  public Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws ExecutionException {
    final List<PyPackage> packages = refreshAndGetPackages(false);
    final Set<PyPackage> dependents = new HashSet<>();
    for (PyPackage p : packages) {
      final List<PyRequirement> requirements = p.getRequirements();
      for (PyRequirement requirement : requirements) {
        if (requirement.getName().equals(pkg.getName())) {
          dependents.add(p);
        }
      }
    }
    return dependents;
  }

  @Override
  @NotNull
  public String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws ExecutionException {
    final List<String> args = new ArrayList<>();
    final Sdk sdk = getSdk();
    final LanguageLevel languageLevel = getOrRequestLanguageLevelForSdk(sdk);

    if (languageLevel.isOlderThan(LanguageLevel.PYTHON27)) {
      throw new ExecutionException("Creating virtual environment for Python " + languageLevel + " is not supported. " +
                                   "Upgrade your project interpreter to Python " + LanguageLevel.PYTHON27 + " or newer");
    }

    final boolean usePyVenv = languageLevel.isAtLeast(LanguageLevel.PYTHON33);
    if (usePyVenv) {
      args.add("pyvenv");
      if (useGlobalSite) {
        args.add("--system-site-packages");
      }
      args.add(destinationDir);
      getHelperResult(PACKAGING_TOOL, args, false, true, null);
    }
    else {
      if (useGlobalSite) {
        args.add("--system-site-packages");
      }
      args.add(destinationDir);
      createVirtualEnvForPython2UsingVirtualenvLibrary(args);
    }

    final String binary = PythonSdkUtil.getPythonExecutable(destinationDir);
    final String binaryFallback = destinationDir + mySeparator + "bin" + mySeparator + "python";
    final String path = (binary != null) ? binary : binaryFallback;

    if (usePyVenv) {
      // Still no 'packaging' and 'pysetup3' for Python 3.3rc1, see PEP 405
      final VirtualFile binaryFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      if (binaryFile != null) {
        final ProjectJdkImpl tmpSdk = new ProjectJdkImpl("", PythonSdkType.getInstance());
        tmpSdk.setHomePath(path);
        // Don't save such one-shot SDK with empty name in the cache of PyPackageManagers
        final PyPackageManager manager = new PyPackageManagerImpl(tmpSdk);
        manager.installManagement();
      }
    }
    return path;
  }

  private void createVirtualEnvForPython2UsingVirtualenvLibrary(List<String> args) throws ExecutionException {
    File workingDirectory = null;
    try {
      workingDirectory = FileUtil.createTempDirectory("tmp", "pycharm-management");
      final String workingDirectoryPath = workingDirectory.getPath();
      getPythonProcessResult("-mzipfile", Arrays.asList("-e", getHelperPath(VIRTUALENV_WHEEL_NAME), workingDirectoryPath),
                             false, true, workingDirectoryPath);
      getPythonProcessResult(workingDirectoryPath + "/virtualenv.py", args,
                             false, true, workingDirectoryPath);
    }
    catch (IOException e) {
      throw new ExecutionException("Cannot create temporary build directory", e);
    }
    finally {
      if (workingDirectory != null) {
        FileUtil.delete(workingDirectory);
      }
    }
  }

  @NotNull
  private static LanguageLevel getOrRequestLanguageLevelForSdk(@NotNull Sdk sdk) throws ExecutionException {
    if (sdk instanceof PyDetectedSdk) {
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
      if (flavor != null && sdk.getHomePath() != null) {
        return flavor.getLanguageLevel(sdk.getHomePath());
      }
      throw new ExecutionException("Cannot retrieve the version of the detected SDK: " + sdk.getHomePath());
    }
    // Use the cached version for an already configured SDK
    return PythonSdkType.getLanguageLevelForSdk(sdk);
  }

  @Override
  @Nullable
  public List<PyRequirement> getRequirements(@NotNull Module module) {
    return Optional
      .ofNullable(PyPackageUtil.getRequirementsFromTxt(module))
      .orElseGet(() -> PyPackageUtil.findSetupPyRequires(module));
  }

  @Nullable
  @Override
  public PyRequirement parseRequirement(@NotNull String line) {
    return PyRequirementParser.fromLine(line);
  }

  @NotNull
  @Override
  public List<PyRequirement> parseRequirements(@NotNull String text) {
    return PyRequirementParser.fromText(text);
  }

  @NotNull
  @Override
  public List<PyRequirement> parseRequirements(@NotNull VirtualFile file) {
    return PyRequirementParser.fromFile(file);
  }

  //   public List<PyPackage> refreshAndGetPackagesIfNotInProgress(boolean alwaysRefresh) throws ExecutionException

  @Override
  @NotNull
  public List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException {
    final List<PyPackage> currentPackages = myPackagesCache;
    if (alwaysRefresh || currentPackages == null) {
      myPackagesCache = null;
      try {
        final List<PyPackage> packages = collectPackages();
        LOG.debug("Packages installed in " + mySdk.getName() + ": " + packages);
        myPackagesCache = packages;
        ApplicationManager.getApplication().getMessageBus().syncPublisher(PACKAGE_MANAGER_TOPIC).packagesRefreshed(mySdk);
        return Collections.unmodifiableList(packages);
      }
      catch (ExecutionException e) {
        myPackagesCache = Collections.emptyList();
        throw e;
      }
    }
    return Collections.unmodifiableList(currentPackages);
  }

  private void refreshPackagesSynchronously() {
    PyPackageUtil.updatePackagesSynchronouslyWithGuard(this, myUpdatingCache);
  }

  @Nullable
  private static String getProxyString() {
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

  @NotNull
  private String getHelperResult(@NotNull String helper, @NotNull List<String> args, boolean askForSudo,
                                 boolean showProgress, @Nullable String parentDir) throws ExecutionException {
    final String helperPath = getHelperPath(helper);
    if (helperPath == null) {
      throw new ExecutionException("Cannot find external tool: " + helper);
    }
    return getPythonProcessResult(helperPath, args, askForSudo, showProgress, parentDir);
  }

  @Nullable
  protected String getHelperPath(@NotNull final String helper) throws ExecutionException {
    return PythonHelpersLocator.getHelperPath(helper);
  }

  @NotNull
  private String getPythonProcessResult(@NotNull String path, @NotNull List<String> args, boolean askForSudo,
                                        boolean showProgress, @Nullable String workingDir) throws ExecutionException {
    final ProcessOutput output = getPythonProcessOutput(path, args, askForSudo, showProgress, workingDir);
    final int exitCode = output.getExitCode();
    if (output.isTimeout()) {
      throw new PyExecutionException("Timed out", path, args, output);
    }
    else if (exitCode != 0) {
      throw new PyExecutionException("Non-zero exit code (" + exitCode + ")", path, args, output);
    }
    return output.getStdout();
  }

  @NotNull
  protected ProcessOutput getPythonProcessOutput(@NotNull String helperPath, @NotNull List<String> args, boolean askForSudo,
                                                 boolean showProgress, @Nullable String workingDir) throws ExecutionException {
    final String homePath = getSdk().getHomePath();
    if (homePath == null) {
      throw new ExecutionException("Cannot find Python interpreter for SDK " + mySdk.getName());
    }
    if (workingDir == null) {
      workingDir = new File(homePath).getParent();
    }
    final List<String> cmdline = new ArrayList<>();
    cmdline.add(homePath);
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
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(mySdk);
      if (flavor != null && flavor.commandLinePatcher() != null) {
        flavor.commandLinePatcher().patchCommandLine(commandLine);
      }
      final Process process;
      final boolean useSudo = askForSudo && PySdkExtKt.adminPermissionsNeeded(mySdk);
      if (useSudo) {
        process = ExecUtil.sudo(commandLine, "Please enter your password to make changes in system packages: ");
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
        final String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ?
                               "Permission denied" : "Non-zero exit code (" + exitCode + ")";
        throw new PyExecutionException(message, helperPath, args, result);
      }
      return result;
    }
    catch (IOException e) {
      throw new PyExecutionException(e.getMessage(), helperPath, args);
    }
  }

  @NotNull
  private static List<String> makeSafeToDisplayCommand(@NotNull List<String> cmdline) {
    final List<String> safeCommand = new ArrayList<>(cmdline);
    for (int i = 0; i < safeCommand.size(); i++) {
      if (cmdline.get(i).equals("--proxy") && i + 1 < cmdline.size()) {
        safeCommand.set(i + 1, makeSafeProxyArgument(cmdline.get(i + 1)));
      }
    }
    return safeCommand;
  }

  @NotNull
  private static String makeSafeProxyArgument(@NotNull String proxyArgument) {
    try {
      final URI proxyUri = new URI(proxyArgument);
      final String credentials = proxyUri.getUserInfo();
      if (credentials != null) {
        final int colonIndex = credentials.indexOf(":");
        if (colonIndex >= 0) {
          final String login = credentials.substring(0, colonIndex);
          final String password = credentials.substring(colonIndex + 1);
          final String maskedPassword = StringUtil.repeatSymbol('*', password.length());
          final String maskedCredentials = login + ":" + maskedPassword;
          return proxyArgument.replaceFirst(Pattern.quote(credentials), maskedCredentials);
        }
      }
    }
    catch (URISyntaxException ignored) {
    }
    return proxyArgument;
  }

  @NotNull
  private List<PyPackage> parsePackagingToolOutput(@NotNull String s) throws ExecutionException {
    final String[] lines = StringUtil.splitByLines(s);
    final List<PyPackage> packages = new ArrayList<>();
    for (String line : lines) {
      final List<String> fields = StringUtil.split(line, "\t");
      if (fields.size() < 3) {
        throw new PyExecutionException("Invalid output format", PACKAGING_TOOL, Collections.emptyList());
      }
      final String name = fields.get(0);
      final String version = fields.get(1);
      final String location = fields.get(2);
      final List<PyRequirement> requirements = new ArrayList<>();
      if (fields.size() >= 4) {
        final String requiresLine = fields.get(3);
        final String requiresSpec = StringUtil.join(StringUtil.split(requiresLine, ":"), "\n");
        requirements.addAll(parseRequirements(requiresSpec));
      }
      if (!"Python".equals(name)) {
        packages.add(new PyPackage(name, version, location, requirements));
      }
    }
    return packages;
  }
}
