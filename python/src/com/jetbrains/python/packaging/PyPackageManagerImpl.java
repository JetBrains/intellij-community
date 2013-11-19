/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.remotesdk.RemoteFile;
import com.intellij.remotesdk.RemoteSdkData;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author vlan
 */
@SuppressWarnings({"UnusedDeclaration", "FieldAccessedSynchronizedAndUnsynchronized"})
public class PyPackageManagerImpl extends PyPackageManager {
  private static final Logger LOG = Logger.getInstance(PyPackageManagerImpl.class);

  public static final int OK = 0;
  public static final int ERROR_WRONG_USAGE = 1;
  public static final int ERROR_NO_PIP = 2;
  public static final int ERROR_NO_SETUPTOOLS = 3;
  public static final int ERROR_INVALID_SDK = -1;
  public static final int ERROR_TOOL_NOT_FOUND = -2;
  public static final int ERROR_TIMEOUT = -3;
  public static final int ERROR_INVALID_OUTPUT = -4;
  public static final int ERROR_ACCESS_DENIED = -5;
  public static final int ERROR_EXECUTION = -6;
  public static final int ERROR_INTERRUPTED = -7;

  public static final String PACKAGE_PIP = "pip";
  public static final String PACKAGE_DISTRIBUTE = "distribute";
  public static final String PACKAGE_SETUPTOOLS = "setuptools";

  public static final Key<Boolean> RUNNING_PACKAGING_TASKS = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks");

  private static final String PACKAGING_TOOL = "packaging_tool.py";
  private static final String VIRTUALENV = "virtualenv.py";
  private static final int TIMEOUT = 10 * 60 * 1000;

  private static final String BUILD_DIR_OPTION = "--build-dir";
  public static final String USE_USER_SITE = "--user";

  public static final String INSTALL = "install";
  public static final String UNINSTALL = "uninstall";
  public static final String UNTAR = "untar";

  // Bundled versions of  package management tools
  public static final String SETUPTOOLS_VERSION = "1.1.5";
  public static final String PIP_VERSION = "1.4.1";

  public static final String SETUPTOOLS = PACKAGE_SETUPTOOLS + "-" + SETUPTOOLS_VERSION;
  public static final String PIP = PACKAGE_PIP + "-" + PIP_VERSION;

  private List<PyPackage> myPackagesCache = null;
  private Map<String, Set<PyPackage>> myDependenciesCache = null;
  private PyExternalProcessException myExceptionCache = null;

  private Sdk mySdk;

  public static class UI {
    @Nullable private Listener myListener;
    @NotNull private Project myProject;
    @NotNull private Sdk mySdk;

    public interface Listener {
      void started();

      void finished(List<PyExternalProcessException> exceptions);
    }

    public UI(@NotNull Project project, @NotNull Sdk sdk, @Nullable Listener listener) {
      myProject = project;
      mySdk = sdk;
      myListener = listener;
    }

    public void installManagement(@NotNull final String name) {
      final String progressTitle;
      final String successTitle;
      progressTitle = "Installing package " + name;
      successTitle = "Packages installed successfully";
      run(new MultiExternalRunnable() {
        @Override
        public List<PyExternalProcessException> run(@NotNull ProgressIndicator indicator) {
          final List<PyExternalProcessException> exceptions = new ArrayList<PyExternalProcessException>();
          indicator.setText(String.format("Installing package '%s'...", name));
          final PyPackageManagerImpl manager = (PyPackageManagerImpl)PyPackageManagers.getInstance().forSdk(mySdk);
          try {
            manager.installManagement(name);
          }
          catch (PyExternalProcessException e) {
            exceptions.add(e);
          }
          return exceptions;
        }
      }, progressTitle, successTitle, "Installed package " + name,
          "Install package failed");
    }

    public void install(@NotNull final List<PyRequirement> requirements, @NotNull final List<String> extraArgs) {
      final String progressTitle;
      final String successTitle;
      progressTitle = "Installing packages";
      successTitle = "Packages installed successfully";
      run(new MultiExternalRunnable() {
        @Override
        public List<PyExternalProcessException> run(@NotNull ProgressIndicator indicator) {
          final int size = requirements.size();
          final List<PyExternalProcessException> exceptions = new ArrayList<PyExternalProcessException>();
          final PyPackageManagerImpl manager = (PyPackageManagerImpl)PyPackageManagers.getInstance().forSdk(mySdk);
          for (int i = 0; i < size; i++) {
            final PyRequirement requirement = requirements.get(i);
            if (myListener != null) {
              indicator.setText(String.format("Installing package '%s'...", requirement));
              indicator.setFraction((double)i / size);
            }
            try {
              manager.install(list(requirement), extraArgs);
            }
            catch (PyExternalProcessException e) {
              exceptions.add(e);
            }
          }
          manager.refresh();
          return exceptions;
        }
      }, progressTitle, successTitle, "Installed packages: " + PyPackageUtil.requirementsToString(requirements),
          "Install packages failed");
    }

    public void uninstall(@NotNull final List<PyPackage> packages) {
      final String packagesString = StringUtil.join(packages, new Function<PyPackage, String>() {
        @Override
        public String fun(PyPackage pkg) {
          return "'" + pkg.getName() + "'";
        }
      }, ", ");
      if (checkDependents(packages)) return;

      run(new MultiExternalRunnable() {
        @Override
        public List<PyExternalProcessException> run(@NotNull ProgressIndicator indicator) {
          final PyPackageManagerImpl manager = (PyPackageManagerImpl)PyPackageManagers.getInstance().forSdk(mySdk);
          try {
            manager.uninstall(packages);
            return list();
          }
          catch (PyExternalProcessException e) {
            return list(e);
          }
          finally {
            manager.refresh();
          }
        }
      }, "Uninstalling packages", "Packages uninstalled successfully", "Uninstalled packages: " + packagesString,
          "Uninstall packages failed");
    }

    private boolean checkDependents(@NotNull final List<PyPackage> packages) {
      try {
        final Map<String, Set<PyPackage>> dependentPackages = collectDependents(packages, mySdk);
        final int[] warning = {0};
        if (!dependentPackages.isEmpty()) {
          ApplicationManager.getApplication().invokeAndWait(new Runnable() {
            @Override
            public void run() {
              if (dependentPackages.size() == 1) {
                String message = "You are attempting to uninstall ";
                List<String> dep = new ArrayList<String>();
                int size = 1;
                for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
                  final Set<PyPackage> value = entry.getValue();
                  size = value.size();
                  dep.add(entry.getKey() + " package which is required for " + StringUtil.join(value, ", "));
                }
                message += StringUtil.join(dep, "\n");
                message += size == 1 ? " package" : " packages";
                message += "\n\nDo you want to proceed?";
                warning[0] = Messages.showYesNoDialog(message, "Warning",
                                                      AllIcons.General.BalloonWarning);
              }
              else {
                String message = "You are attempting to uninstall packages which are required for another packages.\n\n";
                List<String> dep = new ArrayList<String>();
                for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
                  dep.add(entry.getKey() + " -> " + StringUtil.join(entry.getValue(), ", "));
                }
                message += StringUtil.join(dep, "\n");
                message += "\n\nDo you want to proceed?";
                warning[0] = Messages.showYesNoDialog(message, "Warning",
                                                      AllIcons.General.BalloonWarning);
              }
            }
          }, ModalityState.current());
        }
        if (warning[0] != 0) return true;
      }
      catch (PyExternalProcessException e) {
        LOG.info("Error loading packages dependents: " + e.getMessage(), e);
      }
      return false;
    }

    private interface MultiExternalRunnable {
      List<PyExternalProcessException> run(@NotNull ProgressIndicator indicator);
    }

    private void run(@NotNull final MultiExternalRunnable runnable, @NotNull final String progressTitle,
                     @NotNull final String successTitle, @NotNull final String successDescription, @NotNull final String failureTitle) {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, progressTitle, false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setText(progressTitle + "...");
          final Ref<Notification> notificationRef = new Ref<Notification>(null);
          final String PACKAGING_GROUP_ID = "Packaging";
          final Application application = ApplicationManager.getApplication();
          if (myListener != null) {
            application.invokeLater(new Runnable() {
              @Override
              public void run() {
                myListener.started();
              }
            });
          }

          final List<PyExternalProcessException> exceptions = runnable.run(indicator);
          if (exceptions.isEmpty()) {
            notificationRef.set(new Notification(PACKAGING_GROUP_ID, successTitle, successDescription, NotificationType.INFORMATION));
          }
          else {
            final String progressLower = progressTitle.toLowerCase();
            final String firstLine = String.format("Error%s occurred when %s.", exceptions.size() > 1 ? "s" : "", progressLower);

            final String description = createDescription(exceptions, firstLine);
            notificationRef.set(new Notification(PACKAGING_GROUP_ID, failureTitle,
                                                 firstLine + " <a href=\"xxx\">Details...</a>",
                                                 NotificationType.ERROR,
                                                 new NotificationListener() {
                                                   @Override
                                                   public void hyperlinkUpdate(@NotNull Notification notification,
                                                                               @NotNull HyperlinkEvent event) {
                                                     assert myProject != null;
                                                     PackagesNotificationPanel.showError(myProject, failureTitle, description);
                                                   }
                                                 }));
          }
          application.invokeLater(new Runnable() {
            @Override
            public void run() {
              if (myListener != null) {
                myListener.finished(exceptions);
              }
              final Notification notification = notificationRef.get();
              if (notification != null) {
                notification.notify(myProject);
              }
            }
          });
        }
      });
    }

    public static String createDescription(List<PyExternalProcessException> exceptions, String firstLine) {
      final StringBuilder b = new StringBuilder();
      b.append(firstLine);
      b.append("\n\n");
      for (PyExternalProcessException exception : exceptions) {
        b.append(exception.toString());
        b.append("\n");
      }
      return b.toString();
    }
  }

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

  private void installManagement(String name) throws PyExternalProcessException {
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
      output = getProcessOutput(fileName, Collections.<String>singletonList(INSTALL), true, dirName + name);
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
      FileUtil.delete(new File(dirName)); //TODO: remove temp directory for remote interpreter
    }
  }

  PyPackageManagerImpl(@NotNull Sdk sdk) {
    mySdk = sdk;
    final Application app = ApplicationManager.getApplication();
    final MessageBusConnection connection = app.getMessageBus().connect();
    if (!PySdkUtil.isRemote(sdk)) {
      connection.subscribe(VirtualFileManager.VFS_CHANGES, new MySdkRootWatcher());
    }
  }

  public Sdk getSdk() {
    return mySdk;
  }

  @Override
  public void install(String requirementString) throws PyExternalProcessException {
    install(Collections.singletonList(PyRequirement.fromString(requirementString)), Collections.<String>emptyList());
  }

  public void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs)
    throws PyExternalProcessException {
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
      args.addAll(list(BUILD_DIR_OPTION, buildDir.getAbsolutePath()));
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
          if (location != null)
            canModify = FileUtil.ensureCanCreateFile(new File(location));
        }
        args.add(pkg.getName());
      }
      runPythonHelper(PACKAGING_TOOL, args, !canModify);
    }
    finally {
      clearCaches();
    }
  }

  private static Map<String, Set<PyPackage>> collectDependents(@NotNull final List<PyPackage> packages, Sdk sdk) throws PyExternalProcessException {
    Map<String, Set<PyPackage>> dependentPackages = new HashMap<String, Set<PyPackage>>();
    for (PyPackage pkg : packages) {
      final Set<PyPackage> dependents =
        ((PyPackageManagerImpl)PyPackageManager.getInstance(sdk)).getDependents(pkg.getName());
      if (dependents != null && !dependents.isEmpty()) {
        for (PyPackage dependent : dependents) {
          if (!packages.contains(dependent))
            dependentPackages.put(pkg.getName(), dependents);
        }

      }
    }
    return dependentPackages;
  }

  public static String getUserSite() {
    if (SystemInfo.isWindows) {
      final String appdata = System.getenv("APPDATA");
      return appdata + File.separator + "Python";
    }
    else {
      final String userHome = SystemProperties.getUserHome();
      return userHome + File.separator + ".local";
    }
  }


  public boolean cacheIsNotNull() {
    return myPackagesCache != null;
  }

  /**
   * Returns the list of packages for the SDK without initiating a remote connection. Returns null
   * for a remote interpreter if the list of packages was not loaded.
   *
   * @return the list of packages or null
   */
  @Nullable
  public synchronized List<PyPackage> getPackagesFast() throws PyExternalProcessException {
    if (myPackagesCache != null) {
      return myPackagesCache;
    }
    if (PySdkUtil.isRemote(mySdk)) {
      return null;
    }
    return getPackages();
  }

  @NotNull
  public synchronized List<PyPackage> getPackages() throws PyExternalProcessException {
    if (myPackagesCache == null) {
      if (myExceptionCache != null) {
        throw myExceptionCache;
      }

      loadPackages();
    }
    return myPackagesCache;
  }

  public synchronized Set<PyPackage> getDependents(String pkg) throws PyExternalProcessException {
    if (myDependenciesCache == null) {
      if (myExceptionCache != null) {
        throw myExceptionCache;
      }

      loadPackages();
    }
    return myDependenciesCache.get(pkg);
  }

  public synchronized void loadPackages() throws PyExternalProcessException {
    try {
      final String output = runPythonHelper(PACKAGING_TOOL, list("list"));
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

  private void calculateDependents() {
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

  @Nullable
  public PyPackage findPackage(String name) throws PyExternalProcessException {
    return findPackageByName(name, getPackages());
  }

  @Nullable
  public PyPackage findPackageFast(String name) throws PyExternalProcessException {
    final List<PyPackage> packages = getPackagesFast();
    return packages != null ? findPackageByName(name, packages) : null;
  }

  private static PyPackage findPackageByName(String name, List<PyPackage> packages) {
    for (PyPackage pkg : packages) {
      if (name.equalsIgnoreCase(pkg.getName())) {
        return pkg;
      }
    }
    return null;
  }

  public boolean hasPip() {
    try {
      return findPackageFast(PACKAGE_PIP) != null;
    }
    catch (PyExternalProcessException e) {
      return false;
    }
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
      // TODO: Still no 'packaging' and 'pysetup3' for Python 3.3rc1, see PEP 405
      final VirtualFile binaryFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      if (binaryFile != null) {
        final ProjectJdkImpl tmpSdk = new ProjectJdkImpl("", PythonSdkType.getInstance());
        tmpSdk.setHomePath(path);
        final PyPackageManagerImpl manager = new PyPackageManagerImpl(tmpSdk);
        manager.installManagement(SETUPTOOLS);
        manager.installManagement(PIP);
      }
    }
    return path;
  }

  public static void deleteVirtualEnv(@NotNull String sdkHome) throws PyExternalProcessException {
    final File root = PythonSdkType.getVirtualEnvRoot(sdkHome);
    if (root != null) {
      FileUtil.delete(root);
    }
  }

  @Nullable
  public static List<PyRequirement> getRequirements(@NotNull Module module) {
    // TODO: Cache requirements, clear cache on requirements.txt or setup.py updates
    List<PyRequirement> requirements = getRequirementsFromTxt(module);
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

  @Nullable
  public static List<PyRequirement> getRequirementsFromTxt(Module module) {
    final VirtualFile requirementsTxt = PyPackageUtil.findRequirementsTxt(module);
    if (requirementsTxt != null) {
      return PyRequirement.parse(requirementsTxt);
    }
    return null;
  }

  private void clearCaches() {
    myPackagesCache = null;
    myDependenciesCache = null;
    myExceptionCache = null;
  }

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
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
      return credentials + String.format("%s:%d", settings.PROXY_HOST, settings.PROXY_PORT);
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
  private String getHelperPath(String helper) {
    String helperPath;
    final SdkAdditionalData sdkData = mySdk.getSdkAdditionalData();
    if (sdkData instanceof RemoteSdkData) {
      final RemoteSdkData remoteSdkData = (RemoteSdkData)sdkData;
      if (!StringUtil.isEmpty(remoteSdkData.getHelpersPath())) {
        helperPath = new RemoteFile(remoteSdkData.getHelpersPath(),
                                    helper).getPath();
      }
      else {
        helperPath = null;
      }
    }
    else {
      helperPath = PythonHelpersLocator.getHelperPath(helper);
    }
    return helperPath;
  }

  private ProcessOutput getProcessOutput(@NotNull String helperPath,
                                         @NotNull List<String> args,
                                         boolean askForSudo,
                                         @Nullable String workingDir)
    throws PyExternalProcessException {
    final SdkAdditionalData sdkData = mySdk.getSdkAdditionalData();
    final String homePath = mySdk.getHomePath();
    if (homePath == null) {
      throw new PyExternalProcessException(ERROR_INVALID_SDK, helperPath, args, "Cannot find interpreter for SDK");
    }
    if (sdkData instanceof RemoteSdkData) { //remote interpreter
      final RemoteSdkData remoteSdkData = (RemoteSdkData)sdkData;
      final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        final List<String> cmdline = new ArrayList<String>();
        cmdline.add(homePath);
        cmdline.add(RemoteFile.detectSystemByPath(homePath).createRemoteFile(helperPath).getPath());
        cmdline.addAll(Collections2.transform(args, new com.google.common.base.Function<String, String>() {
          @Override
          public String apply(@Nullable String input) {
            return quoteIfNeeded(input);
          }
        }));
        try {
          if (askForSudo) {
            askForSudo = !manager.ensureCanWrite(null, remoteSdkData, remoteSdkData.getInterpreterPath());
          }
          ProcessOutput processOutput;
          do {
            processOutput = manager.runRemoteProcess(null, remoteSdkData, ArrayUtil.toStringArray(cmdline), workingDir, askForSudo);
            if (askForSudo && processOutput.getStderr().contains("sudo: 3 incorrect password attempts")) {
              continue;
            }
            break;
          }
          while (true);
          return processOutput;
        }
        catch (ExecutionException e) {
          throw new PyExternalProcessException(ERROR_INVALID_SDK, helperPath, args, "Error running SDK: " + e.getMessage(), e);
        }
      }
      else {
        throw new PyExternalProcessException(ERROR_INVALID_SDK, helperPath, args,
                                             PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED);
      }
    }
    else {
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
  }

  private static String quoteIfNeeded(String arg) {
    return arg.replace(" ", "\\ ").replace("<", "\\<").replace(">", "\\>");
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


  @Override
  public void showInstallationError(Project project, String title, String description) {
    PackagesNotificationPanel.showError(project, title, description);
  }

  @Override
  public void showInstallationError(Component owner, String title, String description) {
    PackagesNotificationPanel.showError(owner, title, description);
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
