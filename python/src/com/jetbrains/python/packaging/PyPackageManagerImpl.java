/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.HttpConfigurable;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.sdk.PySdkUtil;
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
  // Bundled versions of package management tools
  public static final String SETUPTOOLS_VERSION = "1.1.5";
  public static final String PIP_VERSION = "1.4.1";

  public static final String SETUPTOOLS = PACKAGE_SETUPTOOLS + "-" + SETUPTOOLS_VERSION;
  public static final String PIP = PACKAGE_PIP + "-" + PIP_VERSION;

  public static final int OK = 0;
  public static final int ERROR_NO_PIP = 2;
  public static final int ERROR_NO_SETUPTOOLS = 3;
  public static final int ERROR_INVALID_SDK = -1;
  public static final int ERROR_TOOL_NOT_FOUND = -2;
  public static final int ERROR_TIMEOUT = -3;
  public static final int ERROR_INVALID_OUTPUT = -4;
  public static final int ERROR_ACCESS_DENIED = -5;
  public static final int ERROR_EXECUTION = -6;

  private static final Logger LOG = Logger.getInstance(PyPackageManagerImpl.class);

  private static final String PACKAGING_TOOL = "packaging_tool.py";
  private static final String VIRTUALENV = "virtualenv.py";
  private static final int TIMEOUT = 10 * 60 * 1000;

  private static final String BUILD_DIR_OPTION = "--build-dir";

  public static final String INSTALL = "install";
  public static final String UNINSTALL = "uninstall";
  public static final String UNTAR = "untar";

  private List<PyPackage> myPackagesCache = null;
  private Map<String, Set<PyPackage>> myDependenciesCache = null;
  private PyExternalProcessException myExceptionCache = null;

  protected Sdk mySdk;

  @Override
  public void refresh() {
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {
      @Override
      public void run() {
        application.runWriteAction(new Runnable() {
          @Override
          public void run() {
            final VirtualFile[] files = mySdk.getRootProvider().getFiles(OrderRootType.CLASSES);
            for (VirtualFile file : files) {
              file.refresh(true, true);
            }
          }
        });
        PythonSdkType.getInstance().setupSdkPaths(mySdk);
        clearCaches();
      }
    });
  }

  @Override
  public void installManagement() throws PyExternalProcessException {
    if (!hasPackage(PACKAGE_SETUPTOOLS, false) && !hasPackage(PACKAGE_DISTRIBUTE, false)) {
      installManagement(SETUPTOOLS);
    }
    if (!hasPackage(PACKAGE_PIP, false)) {
      installManagement(PIP);
    }
  }

  @Override
  public boolean hasManagement(boolean cachedOnly) {
    return (hasPackage(PACKAGE_SETUPTOOLS, cachedOnly) || hasPackage(PACKAGE_DISTRIBUTE, cachedOnly)) &&
           hasPackage(PACKAGE_PIP, cachedOnly);
  }

  protected void installManagement(@NotNull String name) throws PyExternalProcessException {
    final String helperPath = getHelperPath(name);

    ArrayList<String> args = Lists.newArrayList(UNTAR, helperPath);

    ProcessOutput output = getHelperOutput(PACKAGING_TOOL, args, false, null);

    if (output.getExitCode() != 0) {
      throw new PyExternalProcessException(output.getExitCode(), PACKAGING_TOOL,
                                           args, output.getStderr());
    }
    String dirName = FileUtil.toSystemDependentName(output.getStdout().trim());
    if (!dirName.endsWith(File.separator)) {
      dirName += File.separator;
    }
    final String fileName = dirName + name + File.separatorChar + "setup.py";
    try {
      output = getProcessOutput(fileName, Collections.singletonList(INSTALL), true, dirName + name);
      final int retcode = output.getExitCode();
      if (output.isTimeout()) {
        throw new PyExternalProcessException(ERROR_TIMEOUT, fileName, Lists.newArrayList(INSTALL), "Timed out");
      }
      else if (retcode != 0) {
        final String stdout = output.getStdout();
        String message = output.getStderr();
        if (message.trim().isEmpty()) {
          message = stdout;
        }
        throw new PyExternalProcessException(retcode, fileName, Lists.newArrayList(INSTALL), message);
      }
    }
    finally {
      clearCaches();
      FileUtil.delete(new File(dirName));
    }
  }

  private boolean hasPackage(@NotNull String name, boolean cachedOnly) {
    try {
      return findPackage(name, cachedOnly) != null;
    }
    catch (PyExternalProcessException ignored) {
      return false;
    }
  }

  PyPackageManagerImpl(@NotNull Sdk sdk) {
    mySdk = sdk;
    subscribeToLocalChanges(sdk);
  }

  protected void subscribeToLocalChanges(Sdk sdk) {
    final Application app = ApplicationManager.getApplication();
    final MessageBusConnection connection = app.getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new MySdkRootWatcher());
  }

  public Sdk getSdk() {
    return mySdk;
  }

  @Override
  public void install(@NotNull String requirementString) throws PyExternalProcessException {
    installManagement();
    install(Collections.singletonList(PyRequirement.fromString(requirementString)), Collections.<String>emptyList());
  }

  @Override
  public void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws PyExternalProcessException {
    final List<String> args = new ArrayList<String>();
    args.add(INSTALL);
    final File buildDir;
    try {
      buildDir = FileUtil.createTempDirectory("pycharm-packaging", null);
    }
    catch (IOException e) {
      throw new PyExternalProcessException(ERROR_ACCESS_DENIED, PACKAGING_TOOL, args, "Cannot create temporary build directory");
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
      runPythonHelper(PACKAGING_TOOL, args, !useUserSite);
    }
    finally {
      clearCaches();
      FileUtil.delete(buildDir);
    }
  }

  public void uninstall(@NotNull List<PyPackage> packages) throws PyExternalProcessException {
    try {
      final List<String> args = new ArrayList<String>();
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
      runPythonHelper(PACKAGING_TOOL, args, !canModify);
    }
    finally {
      clearCaches();
    }
  }

  @Nullable
  public synchronized List<PyPackage> getPackages(boolean cachedOnly) throws PyExternalProcessException {
    if (myPackagesCache == null) {
      if (myExceptionCache != null) {
        throw myExceptionCache;
      }
      if (cachedOnly) {
        return null;
      }
      loadPackages();
    }
    return myPackagesCache;
  }

  @Nullable
  public synchronized Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws PyExternalProcessException {
    if (myDependenciesCache == null) {
      if (myExceptionCache != null) {
        throw myExceptionCache;
      }

      loadPackages();
    }
    return myDependenciesCache.get(pkg.getName());
  }

  public synchronized void loadPackages() throws PyExternalProcessException {
    try {
      final String output = runPythonHelper(PACKAGING_TOOL, Arrays.asList("list"));
      myPackagesCache = parsePackagingToolOutput(output);
      Collections.sort(myPackagesCache, new Comparator<PyPackage>() {
        @Override
        public int compare(PyPackage aPackage, PyPackage aPackage1) {
          return aPackage.getName().compareTo(aPackage1.getName());
        }
      });

      calculateDependents();
    }
    catch (PyExternalProcessException e) {
      myExceptionCache = e;
      LOG.info("Error loading packages list: " + e.getMessage(), e);
      throw e;
    }
  }

  private synchronized void calculateDependents() {
    myDependenciesCache = new HashMap<String, Set<PyPackage>>();
    for (PyPackage p : myPackagesCache) {
      final List<PyRequirement> requirements = p.getRequirements();
      for (PyRequirement requirement : requirements) {
        final String name = requirement.getName();
        Set<PyPackage> value = myDependenciesCache.get(name);
        if (value == null) value = new HashSet<PyPackage>();
        value.add(p);
        myDependenciesCache.put(name, value);
      }
    }
  }

  @Override
  @Nullable
  public PyPackage findPackage(@NotNull String name, boolean cachedOnly) throws PyExternalProcessException {
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

  @NotNull
  public String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws PyExternalProcessException {
    final List<String> args = new ArrayList<String>();
    final boolean usePyVenv = PythonSdkType.getLanguageLevelForSdk(mySdk).isAtLeast(LanguageLevel.PYTHON33);
    if (usePyVenv) {
      args.add("pyvenv");
      if (useGlobalSite) {
        args.add("--system-site-packages");
      }
      args.add(destinationDir);
      runPythonHelper(PACKAGING_TOOL, args);
    }
    else {
      if (useGlobalSite) {
        args.add("--system-site-packages");
      }
      args.add(destinationDir);
      runPythonHelper(VIRTUALENV, args);
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

  protected synchronized void clearCaches() {
    myPackagesCache = null;
    myDependenciesCache = null;
    myExceptionCache = null;
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
  private String runPythonHelper(@NotNull final String helper,
                                 @NotNull final List<String> args, final boolean askForSudo) throws PyExternalProcessException {
    ProcessOutput output = getHelperOutput(helper, args, askForSudo, null);
    final int retcode = output.getExitCode();
    if (output.isTimeout()) {
      throw new PyExternalProcessException(ERROR_TIMEOUT, helper, args, "Timed out");
    }
    else if (retcode != 0) {
      final String message = output.getStderr() + "\n" + output.getStdout();
      throw new PyExternalProcessException(retcode, helper, args, message);
    }
    return output.getStdout();
  }

  @NotNull
  private String runPythonHelper(@NotNull final String helper,
                                 @NotNull final List<String> args) throws PyExternalProcessException {
    return runPythonHelper(helper, args, false);
  }


  private ProcessOutput getHelperOutput(@NotNull String helper,
                                        @NotNull List<String> args,
                                        final boolean askForSudo,
                                        @Nullable String parentDir)
    throws PyExternalProcessException {
    final String helperPath = getHelperPath(helper);

    if (helperPath == null) {
      throw new PyExternalProcessException(ERROR_TOOL_NOT_FOUND, helper, args, "Cannot find external tool");
    }
    return getProcessOutput(helperPath, args, askForSudo, parentDir);
  }

  @Nullable
  protected String getHelperPath(String helper) {
    return PythonHelpersLocator.getHelperPath(helper);
  }

  protected ProcessOutput getProcessOutput(@NotNull String helperPath,
                                         @NotNull List<String> args,
                                         boolean askForSudo,
                                         @Nullable String workingDir) throws PyExternalProcessException {
    final String homePath = mySdk.getHomePath();
    if (homePath == null) {
      throw new PyExternalProcessException(ERROR_INVALID_SDK, helperPath, args, "Cannot find interpreter for SDK");
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
    if (!canCreate && !SystemInfo.isWindows && askForSudo) {   //is system site interpreter --> we need sudo privileges
      try {
        final ProcessOutput result = ExecUtil.sudoAndGetOutput(cmdline,
                                                               "Please enter your password to make changes in system packages: ",
                                                               workingDir);
        String message = result.getStderr();
        if (result.getExitCode() != 0) {
          final String stdout = result.getStdout();
          if (StringUtil.isEmptyOrSpaces(message)) {
            message = stdout;
          }
          if (StringUtil.isEmptyOrSpaces(message)) {
            message = "Failed to perform action. Permission denied.";
          }
          throw new PyExternalProcessException(result.getExitCode(), helperPath, args, message);
        }
        if (SystemInfo.isMac && !StringUtil.isEmptyOrSpaces(message)) {
          throw new PyExternalProcessException(result.getExitCode(), helperPath, args, message);
        }
        return result;
      }
      catch (ExecutionException e) {
        throw new PyExternalProcessException(ERROR_EXECUTION, helperPath, args, e.getMessage());
      }
      catch (IOException e) {
        throw new PyExternalProcessException(ERROR_ACCESS_DENIED, helperPath, args, e.getMessage());
      }
    }
    else {
      return PySdkUtil.getProcessOutput(workingDir, ArrayUtil.toStringArray(cmdline), TIMEOUT);
    }
  }

  @NotNull
  private static List<PyPackage> parsePackagingToolOutput(@NotNull String s) throws PyExternalProcessException {
    final String[] lines = StringUtil.splitByLines(s);
    final List<PyPackage> packages = new ArrayList<PyPackage>();
    for (String line : lines) {
      final List<String> fields = StringUtil.split(line, "\t");
      if (fields.size() < 3) {
        throw new PyExternalProcessException(ERROR_INVALID_OUTPUT, PACKAGING_TOOL, Collections.<String>emptyList(),
                                             "Invalid output format");
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
      final VirtualFile[] roots = mySdk.getRootProvider().getFiles(OrderRootType.CLASSES);
      for (VFileEvent event : events) {
        final VirtualFile file = event.getFile();
        if (file != null) {
          for (VirtualFile root : roots) {
            if (VfsUtilCore.isAncestor(root, file, false)) {
              clearCaches();
              return;
            }
          }
        }
      }
    }
  }
}
