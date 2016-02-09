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
package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.HttpConfigurable;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackageManagerImpl extends PyPackageManager {
  // Python 2.4-2.5 compatible versions
  public static final String SETUPTOOLS_PRE_26_VERSION = "1.4.2";
  public static final String PIP_PRE_26_VERSION = "1.1";
  public static final String VIRTUALENV_PRE_26_VERSION = "1.7.2";

  public static final String SETUPTOOLS_VERSION = "18.1";
  public static final String PIP_VERSION = "7.1.0";
  public static final String VIRTUALENV_VERSION = "13.1.0";

  public static final int OK = 0;
  public static final int ERROR_NO_SETUPTOOLS = 3;

  private static final Logger LOG = Logger.getInstance(PyPackageManagerImpl.class);

  private static final String PACKAGING_TOOL = "packaging_tool.py";
  private static final int TIMEOUT = 10 * 60 * 1000;

  private static final String BUILD_DIR_OPTION = "--build-dir";

  public static final String INSTALL = "install";
  public static final String UNINSTALL = "uninstall";
  public static final String UNTAR = "untar";

  private final Object myCacheLock = new Object();
  private List<PyPackage> myPackagesCache = null;
  private ExecutionException myExceptionCache = null;

  @NotNull final private Sdk mySdk;

  @Override
  public void refresh() {
    LOG.debug("Refreshing SDK roots and packages cache");
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {
      @Override
      public void run() {
        final Sdk sdk = getSdk();
        application.runWriteAction(new Runnable() {
          @Override
          public void run() {
            final VirtualFile[] files = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
            VfsUtil.markDirtyAndRefresh(true, true, true, files);
          }
        });
        PythonSdkType.getInstance().setupSdkPaths(sdk);
        clearCaches();
      }
    });
  }

  @Override
  public void installManagement() throws ExecutionException {
    final Sdk sdk = getSdk();
    final boolean pre26 = PythonSdkType.getLanguageLevelForSdk(sdk).isOlderThan(LanguageLevel.PYTHON26);
    if (!hasSetuptools(false)) {
      final String name = SETUPTOOLS + "-" + (pre26 ? SETUPTOOLS_PRE_26_VERSION : SETUPTOOLS_VERSION);
      installManagement(name);
    }
    if (!hasPackage(PIP, false)) {
      final String name = PIP + "-" + (pre26 ? PIP_PRE_26_VERSION : PIP_VERSION);
      installManagement(name);
    }
  }

  @Override
  public boolean hasManagement(boolean cachedOnly) throws ExecutionException {
    return hasSetuptools(cachedOnly) && hasPackage(PIP, cachedOnly);
  }

  private boolean hasSetuptools(boolean cachedOnly) throws ExecutionException {
    try {
      return hasPackage(SETUPTOOLS, cachedOnly) || hasPackage(DISTRIBUTE, cachedOnly);
    }
    catch (PyExecutionException e) {
      if (e.getExitCode() == ERROR_NO_SETUPTOOLS) {
        return false;
      }
      throw e;
    }
  }

  protected void installManagement(@NotNull String name) throws ExecutionException {
    final String dirName = extractHelper(name + ".tar.gz");
    try {
      final String fileName = dirName + name + File.separatorChar + "setup.py";
      getPythonProcessResult(fileName, Collections.singletonList(INSTALL), true, true, dirName + name);
    }
    finally {
      clearCaches();
      FileUtil.delete(new File(dirName));
    }
  }

  @NotNull
  private String extractHelper(@NotNull String name) throws ExecutionException {
    final String helperPath = getHelperPath(name);
    final ArrayList<String> args = Lists.newArrayList(UNTAR, helperPath);
    final String result = getHelperResult(PACKAGING_TOOL, args, false, false, null);
    String dirName = FileUtil.toSystemDependentName(result.trim());
    if (!dirName.endsWith(File.separator)) {
      dirName += File.separator;
    }
    return dirName;
  }

  private boolean hasPackage(@NotNull String name, boolean cachedOnly) throws ExecutionException {
    return findPackage(name, cachedOnly) != null;
  }

  PyPackageManagerImpl(@NotNull final Sdk sdk) {
    mySdk = sdk;
    subscribeToLocalChanges();
  }

  protected void subscribeToLocalChanges() {
    final Application app = ApplicationManager.getApplication();
    final MessageBusConnection connection = app.getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new MySdkRootWatcher());
  }

  @NotNull
  public Sdk getSdk() {
    return mySdk;
  }

  @Override
  public void install(@NotNull String requirementString) throws ExecutionException {
    installManagement();
    install(Collections.singletonList(PyRequirement.fromString(requirementString)), Collections.<String>emptyList());
  }

  @Override
  public void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException {
    final List<String> args = new ArrayList<String>();
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

    boolean useUserSite = extraArgs.contains(USE_USER_SITE);

    final String proxyString = getProxyString();
    if (proxyString != null) {
      args.add("--proxy");
      args.add(proxyString);
    }
    args.addAll(extraArgs);
    for (PyRequirement req : requirements) {
      args.addAll(req.toOptions());
    }
    try {
      getHelperResult(PACKAGING_TOOL, args, !useUserSite, true, null);
    }
    catch (PyExecutionException e) {
      final List<String> simplifiedArgs = new ArrayList<String>();
      simplifiedArgs.add("install");
      if (proxyString != null) {
        simplifiedArgs.add("--proxy");
        simplifiedArgs.add(proxyString);
      }
      simplifiedArgs.addAll(extraArgs);
      for (PyRequirement req : requirements) {
        simplifiedArgs.addAll(req.toOptions());
      }
      throw new PyExecutionException(e.getMessage(), "pip", simplifiedArgs, e.getStdout(), e.getStderr(), e.getExitCode(), e.getFixes());
    }
    finally {
      LOG.debug("Packages cache is about to be cleared because these requirements were installed: " + requirements);
      clearCaches();
      FileUtil.delete(buildDir);
    }
  }

  public void uninstall(@NotNull List<PyPackage> packages) throws ExecutionException {
    final List<String> args = new ArrayList<String>();
    try {
      args.add(UNINSTALL);
      boolean canModify = true;
      for (PyPackage pkg : packages) {
        if (canModify) {
          final String location = pkg.getLocation();
          if (location != null) {
            canModify = FileUtil.ensureCanCreateFile(new File(location));
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
      LOG.debug("Packages cache is about to be cleared because these packages were uninstalled: " + packages);
      clearCaches();
    }
  }

  @Nullable
  public List<PyPackage> getPackages(boolean cachedOnly) throws ExecutionException {
    synchronized (myCacheLock) {
      if (myPackagesCache != null) {
        return new ArrayList<PyPackage>(myPackagesCache);
      }
      if (myExceptionCache != null) {
        throw myExceptionCache;
      }
      if (cachedOnly) {
        return null;
      }
    }
    try {
      final List<PyPackage> packages = getPackages();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Packages installed in " + mySdk.getName() + ": " + packages);
      }
      synchronized (myCacheLock) {
        myPackagesCache = packages;
        return new ArrayList<PyPackage>(myPackagesCache);
      }
    }
    catch (ExecutionException e) {
      synchronized (myCacheLock) {
        myExceptionCache = e;
      }
      throw e;
    }
  }

  //@NotNull
  //public String fetchLatestVersion(InstalledPackage pkg) throws ExecutionException {
  //  final ArrayList<String> arguments = Lists.newArrayList("latestVersion", pkg.getName());
  //  arguments.addAll(PyPackageService.getInstance().additionalRepositories);
  //  return getHelperResult(PACKAGING_TOOL, arguments, false, false, null);
  //}

  @NotNull
  protected List<PyPackage> getPackages() throws ExecutionException {
    final String output;
    try {
      output = getHelperResult(PACKAGING_TOOL, Collections.singletonList("list"), false, false, null);
    }
    catch (final ProcessNotCreatedException ex) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.info("Not-env unit test mode, will return mock packages");
        return Lists.newArrayList(new PyPackage(PIP, PIP_VERSION, null, Collections.<PyRequirement>emptyList()),
                                  new PyPackage(SETUPTOOLS, SETUPTOOLS_VERSION, null, Collections.<PyRequirement>emptyList()));
      }
      else {
        throw ex;
      }
    }

    return parsePackagingToolOutput(output);
  }

  @Nullable
  public Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws ExecutionException {
    final List<PyPackage> packages = getPackages(false);
    if (packages != null) {
      final Set<PyPackage> dependents = new HashSet<PyPackage>();
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
    return null;
  }

  @Override
  @Nullable
  public PyPackage findPackage(@NotNull String name, boolean cachedOnly) throws ExecutionException {
    final List<PyPackage> packages = getPackages(cachedOnly);
    if (packages != null) {
      for (PyPackage pkg : packages) {
        if (name.equalsIgnoreCase(pkg.getName())) {
          return pkg;
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public final PyPackage findPackage(@NotNull final String name) throws ExecutionException {
    return findPackage(name, PySdkUtil.isRemote(mySdk));
  }

  @NotNull
  public String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws ExecutionException {
    final List<String> args = new ArrayList<String>();
    final Sdk sdk = getSdk();
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
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
      final boolean pre26 = languageLevel.isOlderThan(LanguageLevel.PYTHON26);
      final String name = "virtualenv-" + (pre26 ? VIRTUALENV_PRE_26_VERSION : VIRTUALENV_VERSION);
      final String dirName = extractHelper(name + ".tar.gz");
      try {
        final String fileName = dirName + name + File.separatorChar + "virtualenv.py";
        getPythonProcessResult(fileName, args, false, true, dirName + name);
      }
      finally {
        FileUtil.delete(new File(dirName));
      }
    }

    final String binary = PythonSdkType.getPythonExecutable(destinationDir);
    final String binaryFallback = destinationDir + File.separator + "bin" + File.separator + "python";
    final String path = (binary != null) ? binary : binaryFallback;

    if (usePyVenv) {
      // Still no 'packaging' and 'pysetup3' for Python 3.3rc1, see PEP 405
      final VirtualFile binaryFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      if (binaryFile != null) {
        final ProjectJdkImpl tmpSdk = new ProjectJdkImpl("", PythonSdkType.getInstance());
        tmpSdk.setHomePath(path);
        final PyPackageManager manager = PyPackageManager.getInstance(tmpSdk);
        manager.installManagement();
      }
    }
    return path;
  }

  @Nullable
  public List<PyRequirement> getRequirements(@NotNull Module module) {
    List<PyRequirement> requirements = PySdkUtil.getRequirementsFromTxt(module);
    if (requirements != null) {
      return requirements;
    }
    final List<String> lines = new ArrayList<String>();
    for (String name : PyPackageUtil.SETUP_PY_REQUIRES_KWARGS_NAMES) {
      final PyListLiteralExpression installRequires = PyPackageUtil.findSetupPyRequires(module, name);
      if (installRequires != null) {
        for (PyExpression e : installRequires.getElements()) {
          if (e instanceof PyStringLiteralExpression) {
            lines.add(((PyStringLiteralExpression)e).getStringValue());
          }
        }
      }
    }
    if (!lines.isEmpty()) {
      return PyRequirement.parse(StringUtil.join(lines, "\n"));
    }
    if (PyPackageUtil.findSetupPy(module) != null) {
      return Collections.emptyList();
    }
    return null;
  }

  protected void clearCaches() {
    synchronized (myCacheLock) {
      myPackagesCache = null;
      myExceptionCache = null;
      LOG.debug("Packages cache is cleared");
    }
  }

  @Nullable
  private static String getProxyString() {
    final HttpConfigurable settings = HttpConfigurable.getInstance();
    if (settings != null && settings.USE_HTTP_PROXY) {
      final String credentials;
      if (settings.PROXY_AUTHENTICATION) {
        credentials = String.format("%s:%s@", settings.PROXY_LOGIN, settings.getPlainProxyPassword());
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
  protected String getHelperPath(String helper) throws ExecutionException {
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
    final List<String> cmdline = new ArrayList<String>();
    cmdline.add(homePath);
    cmdline.add(helperPath);
    cmdline.addAll(args);
    LOG.info("Running packaging tool: " + StringUtil.join(cmdline, " "));

    final boolean canCreate = FileUtil.ensureCanCreateFile(new File(homePath));
    final boolean useSudo = !canCreate && !SystemInfo.isWindows && askForSudo;

    try {
      final Map<String, String> environment = new HashMap<String, String>(System.getenv());
      PythonEnvUtil.setPythonUnbuffered(environment);
      PythonEnvUtil.setPythonDontWriteBytecode(environment);
      GeneralCommandLine commandLine = new GeneralCommandLine(cmdline).withWorkDirectory(workingDir).withEnvironment(environment);
      Process process;
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
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void onTextAvailable(ProcessEvent event, Key outputType) {
            if (outputType == ProcessOutputTypes.STDOUT || outputType == ProcessOutputTypes.STDERR) {
              for (String line : StringUtil.splitByLines(event.getText())) {
                final String trimmed = line.trim();
                if (isMeaningfulOutput(trimmed)) {
                  indicator.setText2(trimmed);
                }
              }
            }
          }

          private boolean isMeaningfulOutput(@NotNull String trimmed) {
            return trimmed.length() > 3;
          }
        });
        result = handler.runProcessWithProgressIndicator(indicator);
      }
      else {
        result = handler.runProcess(TIMEOUT);
      }
      if (result.isCancelled()) {
        throw new RunCanceledByUserException();
      }
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
  private static List<PyPackage> parsePackagingToolOutput(@NotNull String s) throws ExecutionException {
    final String[] lines = StringUtil.splitByLines(s);
    final List<PyPackage> packages = new ArrayList<PyPackage>();
    for (String line : lines) {
      final List<String> fields = StringUtil.split(line, "\t");
      if (fields.size() < 3) {
        throw new PyExecutionException("Invalid output format", PACKAGING_TOOL, Collections.<String>emptyList());
      }
      final String name = fields.get(0);
      final String version = fields.get(1);
      final String location = fields.get(2);
      final List<PyRequirement> requirements = new ArrayList<PyRequirement>();
      if (fields.size() >= 4) {
        final String requiresLine = fields.get(3);
        final String requiresSpec = StringUtil.join(StringUtil.split(requiresLine, ":"), "\n");
        requirements.addAll(PyRequirement.parse(requiresSpec));
      }
      if (!"Python".equals(name)) {
        packages.add(new PyPackage(name, version, location, requirements));
      }
    }
    return packages;
  }

  private class MySdkRootWatcher extends BulkFileListener.Adapter {
    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      final Sdk sdk = getSdk();
      final VirtualFile[] roots = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
      for (VFileEvent event : events) {
        final VirtualFile file = event.getFile();
        if (file != null) {
          for (VirtualFile root : roots) {
            if (VfsUtilCore.isAncestor(root, file, false)) {
              LOG.debug("Clearing packages cache on SDK change");
              clearCaches();
              return;
            }
          }
        }
      }
    }
  }
}