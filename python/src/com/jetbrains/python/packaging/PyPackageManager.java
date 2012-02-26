package com.jetbrains.python.packaging;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.remote.PyRemoteInterpreterException;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.PythonRemoteSdkAdditionalData;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackageManager {
  public static final int OK = 0;
  public static final int ERROR_WRONG_USAGE = 1;
  public static final int ERROR_NO_PACKAGING_TOOLS = 2;
  public static final int ERROR_INVALID_SDK = -1;
  public static final int ERROR_TOOL_NOT_FOUND = -2;
  public static final int ERROR_TIMEOUT = -3;
  public static final int ERROR_INVALID_OUTPUT = -4;
  public static final int ERROR_ACCESS_DENIED = -5;

  public static final Key<Boolean> RUNNING_PACKAGING_TASKS = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks");

  private static final String PACKAGING_TOOL = "packaging_tool.py";
  private static final String VIRTUALENV = "virtualenv.py";
  private static final int TIMEOUT = 10 * 60 * 1000;

  // Sdk instances are re-created by ProjectSdksModel on every refresh so we cannot use them as keys for caching
  private static final Map<String, PyPackageManager> ourInstances = new HashMap<String, PyPackageManager>();
  private static final String BUILD_DIR_OPTION = "--build-dir";

  private List<PyPackage> myPackagesCache = null;
  private Sdk mySdk;

  public static class UI {
    @Nullable private Listener myListener;
    @NotNull private Project myProject;
    @NotNull private Sdk mySdk;

    public interface Listener {
      void started();
      void finished(@Nullable PyExternalProcessException exception);
    }

    public UI(@NotNull Project project, @NotNull Sdk sdk, @Nullable Listener listener) {
      myProject = project;
      mySdk = sdk;
      myListener = listener;
    }

    public void install(@NotNull final List<PyRequirement> requirements, @NotNull final List<String> extraArgs) {
      final String progressTitle;
      final String successTitle;
      if (requirements.size() == 1) {
        final PyRequirement req = requirements.get(0);
        progressTitle = String.format("Installing package '%s'", req);
        successTitle = String.format("Package '%s' installed successfully", req);
      }
      else {
        progressTitle = "Installing packages";
        successTitle = "Packages installed successfully";
      }
      run(new ExternalRunnable() {
        @Override
        public void run() throws PyExternalProcessException {
          PyPackageManager.getInstance(mySdk).install(requirements, extraArgs);
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

      run(new ExternalRunnable() {
        @Override
        public void run() throws PyExternalProcessException {
          PyPackageManager.getInstance(mySdk).uninstall(packages);
        }
      }, "Uninstalling packages", "Packages uninstalled successfully", "Uninstalled packages: " + packagesString,
         "Uninstall packages failed");
    }

    private interface ExternalRunnable {
      void run() throws PyExternalProcessException;
    }

    private void run(@NotNull final ExternalRunnable runnable, @NotNull final String progressTitle,
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
          final Ref<PyExternalProcessException> exceptionRef = Ref.create(null);
          try {
            runnable.run();
            notificationRef.set(new Notification(PACKAGING_GROUP_ID, successTitle, successDescription, NotificationType.INFORMATION));
          }
          catch (final PyExternalProcessException e) {
            exceptionRef.set(e);
            final String progressLower = progressTitle.toLowerCase();
            final String description = "Error occurred when " + progressLower + ".";
            final String command = e.getName() + " " + StringUtil.join(e.getArgs(), " ");
            notificationRef.set(new Notification(PACKAGING_GROUP_ID, failureTitle,
                                                 String.format("Error occurred when %s. <a href=\"xxx\">Details...</a>",
                                                               progressLower),
                                                 NotificationType.ERROR,
                                                 new NotificationListener() {
                                                   @Override
                                                   public void hyperlinkUpdate(@NotNull Notification notification,
                                                                               @NotNull HyperlinkEvent event) {
                                                     PyPIPackageUtil.showError(myProject, failureTitle, description, command, e.getMessage());
                                                     notification.expire();
                                                   }
                                                 }));
          }
          finally {
            application.invokeLater(new Runnable() {
              @Override
              public void run() {
                if (myListener != null) {
                  myListener.finished(exceptionRef.get());
                }
                VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
                PythonSdkType.getInstance().setupSdkPaths(mySdk);
                final Notification notification = notificationRef.get();
                if (notification != null) {
                  notification.notify(myProject);
                }
              }
            });
          }
        }
      });
    }
  }

  private PyPackageManager(@NotNull Sdk sdk) {
    mySdk = sdk;
  }

  @NotNull
  public static PyPackageManager getInstance(@NotNull Sdk sdk) {
    final String name = sdk.getName();
    PyPackageManager manager = ourInstances.get(name);
    if (manager == null) {
      manager = new PyPackageManager(sdk);
      ourInstances.put(name, manager);
    }
    return manager;
  }

  public Sdk getSdk() {
    return mySdk;
  }

  public void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws PyExternalProcessException {
    final List<String> args = new ArrayList<String>();
    args.add("install");
    final File buildDir;
    try {
      buildDir = FileUtil.createTempDirectory("packaging", null);
    }
    catch (IOException e) {
      throw new PyExternalProcessException(ERROR_ACCESS_DENIED, PACKAGING_TOOL, args, "Cannot create temporary build directory");
    }
    if (!extraArgs.contains(BUILD_DIR_OPTION)) {
      args.addAll(list(BUILD_DIR_OPTION, buildDir.getAbsolutePath()));
    }
    args.addAll(extraArgs);
    for (PyRequirement req : requirements) {
      args.add(req.toString());
    }
    try {
      runPythonHelper(PACKAGING_TOOL, args);
    }
    finally {
      clearCaches();
      FileUtil.delete(buildDir);
    }
  }

  public void uninstall(@NotNull List<PyPackage> packages) throws PyExternalProcessException {
    try {
      final List<String> args = new ArrayList<String>();
      args.add("uninstall");
      for (PyPackage pkg : packages) {
        args.add(pkg.getName());
      }
      runPythonHelper(PACKAGING_TOOL, args);
    }
    finally {
      clearCaches();
    }
  }

  @NotNull
  public List<PyPackage> getPackages() throws PyExternalProcessException {
    if (myPackagesCache == null) {
      final String output = runPythonHelper(PACKAGING_TOOL, list("list"));
      myPackagesCache = parsePackagingToolOutput(output);
      Collections.sort(myPackagesCache, new Comparator<PyPackage>() {
        @Override
        public int compare(PyPackage aPackage, PyPackage aPackage1) {
          return aPackage.getName().compareTo(aPackage1.getName());
        }
      });
    }
    return myPackagesCache;
  }

  @Nullable
  public PyPackage findPackage(String name){
    try {
      for (PyPackage pkg : getPackages()) {
        if (name.equals(pkg.getName())) {
          return pkg;
        }
      }
    }
    catch (PyExternalProcessException e) {
    }
    return null;
  }

  @NotNull
  public String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws PyExternalProcessException {
    List<String> args = new ArrayList<String>();
    if (useGlobalSite) args.add("--system-site-packages");
    args.addAll(list("--never-download", "--distribute", destinationDir));

    runPythonHelper(VIRTUALENV, args);
    final String binary = PythonSdkType.getPythonExecutable(destinationDir);
    final String binaryFallback = destinationDir + File.separator + "bin" + File.separator + "python";
    return (binary != null) ? binary : binaryFallback;
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
    final Document requirementsTxt = PyPackageUtil.findRequirementsTxt(module);
    if (requirementsTxt != null) {
      return PyRequirement.parse(requirementsTxt.getText());
    }
    final PyListLiteralExpression installRequires = PyPackageUtil.findSetupPyInstallRequires(module);
    if (installRequires != null) {
      final List<String> lines = new ArrayList<String>();
      for (PyExpression e : installRequires.getElements()) {
        if (e instanceof PyStringLiteralExpression) {
          lines.add(((PyStringLiteralExpression)e).getStringValue());
        }
      }
      return PyRequirement.parse(StringUtil.join(lines, "\n"));
    }
    if (PyPackageUtil.findSetupPy(module) != null) {
      return Collections.emptyList();
    }
    return null;
  }

  public void clearCaches() {
    myPackagesCache = null;
  }

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
  }

  @NotNull
  private String runPythonHelper(@NotNull final String helper,
                                 @NotNull final List<String> args) throws PyExternalProcessException {
    ProcessOutput output = getProcessOutput(helper, args);
    final int retcode = output.getExitCode();
    if (output.isTimeout()) {
      throw new PyExternalProcessException(ERROR_TIMEOUT, helper, args, "Timed out");
    }
    else if (retcode != 0) {
      final String stdout = output.getStdout();
      String message = output.getStderr();
      if (message.trim().isEmpty()) {
        message = stdout;
      }
      throw new PyExternalProcessException(retcode, helper, args, message);
    }
    return output.getStdout();
  }


  private ProcessOutput getProcessOutput(@NotNull String helper, @NotNull List<String> args) throws PyExternalProcessException {
    final SdkAdditionalData sdkData = mySdk.getSdkAdditionalData();
    if (sdkData instanceof PythonRemoteSdkAdditionalData) {
      final PythonRemoteSdkAdditionalData remoteSdkData = (PythonRemoteSdkAdditionalData)sdkData;
      final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        final List<String> cmdline = new ArrayList<String>();
        cmdline.add(mySdk.getHomePath());
        cmdline.add(new File(remoteSdkData.getPyCharmTempFilesPath(),
                             helper).getPath());
        cmdline.addAll(args);
        try {
          return manager.runRemoteProcess(null, remoteSdkData, ArrayUtil.toStringArray(cmdline));
        }
        catch (PyRemoteInterpreterException e) {
          throw new PyExternalProcessException(ERROR_INVALID_SDK, helper, args, "Error running SDK");
        }
      }
      else {
        throw new PyExternalProcessException(ERROR_INVALID_SDK, helper, args,
                                             "Remote interpreter can't be executed. Please enable WebDeployment plugin.");
      }
    }
    else {
      final String homePath = mySdk.getHomePath();
      if (homePath == null) {
        throw new PyExternalProcessException(ERROR_INVALID_SDK, helper, args, "Cannot find interpreter for SDK");
      }
      final String helperPath = PythonHelpersLocator.getHelperPath(helper);
      if (helperPath == null) {
        throw new PyExternalProcessException(ERROR_TOOL_NOT_FOUND, helper, args, "Cannot find external tool");
      }
      final String parentDir = new File(homePath).getParent();
      final List<String> cmdline = new ArrayList<String>();
      cmdline.add(homePath);
      cmdline.add(helperPath);
      cmdline.addAll(args);
      return PySdkUtil.getProcessOutput(parentDir, ArrayUtil.toStringArray(cmdline), TIMEOUT);
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
      if (!"Python".equals(name) && !"wsgiref".equals(name)) {
        packages.add(new PyPackage(name, version, location, new ArrayList<PyRequirement>()));
      }
    }
    return packages;
  }
}
