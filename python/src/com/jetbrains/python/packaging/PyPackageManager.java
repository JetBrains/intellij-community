package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.remote.PyRemoteInterpreterException;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.PythonRemoteSdkAdditionalData;
import com.jetbrains.python.remote.RemoteFile;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptException;
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
  public static final int ERROR_EXECUTION = -6;
  public static final int ERROR_INTERRUPTED = -7;

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
          final PyPackageManager manager = PyPackageManager.getInstance(mySdk);
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
          final PyPackageManager manager = PyPackageManager.getInstance(mySdk);
          for (int i = 0; i < size; i++) {
            final PyRequirement requirement = requirements.get(i);
            if (myListener != null) {
              indicator.setText(String.format("Installing package '%s'...", requirement));
              indicator.setFraction((double)i / size);
            }
            try {
              final boolean useUserSite = PyPackageService.getInstance(myProject).useUserSite(mySdk.getHomePath());
              manager.install(list(requirement), extraArgs, useUserSite);
            }
            catch (PyExternalProcessException e) {
              exceptions.add(e);
            }
          }
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

      run(new MultiExternalRunnable() {
        @Override
        public List<PyExternalProcessException> run(@NotNull ProgressIndicator indicator) {
          try {
            PyPackageManager.getInstance(mySdk).uninstall(packages);
            return list();
          }
          catch (PyExternalProcessException e) {
            return list(e);
          }
        }
      }, "Uninstalling packages", "Packages uninstalled successfully", "Uninstalled packages: " + packagesString,
         "Uninstall packages failed");
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
                                                     PyPIPackageUtil.showError(myProject, failureTitle,
                                                                               description);
                                                   }
                                                 }));
          }
          application.invokeLater(new Runnable() {
            @Override
            public void run() {
              if (myListener != null) {
                myListener.finished(exceptions);
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

  private void installManagement(String name) throws PyExternalProcessException {
    final File helperFile = PythonHelpersLocator.getHelperFile(name + ".tar.gz");
    ProcessOutput output = PySdkUtil.getProcessOutput(helperFile.getParent(),
                                                      new String[]{mySdk.getHomePath(),
                                                          PACKAGING_TOOL, "untar", name});

    if (output.getExitCode() != 0) {
      throw new PyExternalProcessException(output.getExitCode(), PACKAGING_TOOL,
                                           Lists.newArrayList("untar"), output.getStderr());
    }
    final String dirName = output.getStdout().trim();
    final String fileName = dirName + File.separatorChar + name + File.separatorChar + "setup.py";
    final File setupFile = new File(fileName);
    try {
      output = getProcessOutput(setupFile.getAbsolutePath(), Collections.<String>singletonList("install"), true, setupFile.getParent());
      final int retcode = output.getExitCode();
      if (output.isTimeout()) {
        throw new PyExternalProcessException(ERROR_TIMEOUT, name, Lists.newArrayList("untar"), "Timed out");
      }
      else if (retcode != 0) {
        final String stdout = output.getStdout();
        String message = output.getStderr();
        if (message.trim().isEmpty()) {
          message = stdout;
        }
        throw new PyExternalProcessException(retcode, name, Lists.newArrayList("untar"), message);
      }
    }
    finally{
      FileUtil.delete(new File(dirName));
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

  public void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs, final boolean useUserSite)
      throws PyExternalProcessException {
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
      args.add("uninstall");
      boolean canModify = true;
      for (PyPackage pkg : packages) {
        if (canModify)
          canModify = FileUtil.ensureCanCreateFile(new File(pkg.getLocation()));
        args.add(pkg.getName());
      }
      runPythonHelper(PACKAGING_TOOL, args, !canModify);
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
  public PyPackage findPackage(String name) throws PyExternalProcessException {
    for (PyPackage pkg : getPackages()) {
      if (name.equals(pkg.getName())) {
        return pkg;
      }
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

  public void clearCaches() {
    myPackagesCache = null;
  }

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
  }

  @NotNull
  private String runPythonHelper(@NotNull final String helper,
                                 @NotNull final List<String> args, final boolean askForSudo) throws PyExternalProcessException {
    final String helperPath = PythonHelpersLocator.getHelperPath(helper);
    if (helperPath == null) {
      throw new PyExternalProcessException(ERROR_TOOL_NOT_FOUND, helper, args, "Cannot find external tool");
    }
    ProcessOutput output = getProcessOutput(helperPath, args, askForSudo, null);
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

  @NotNull
  private String runPythonHelper(@NotNull final String helper,
                                 @NotNull final List<String> args) throws PyExternalProcessException {
    return runPythonHelper(helper, args, false);
  }

  private ProcessOutput getProcessOutput(@NotNull String helper, @NotNull List<String> args, final boolean askForSudo, @Nullable String parentDir)
      throws PyExternalProcessException {
    final SdkAdditionalData sdkData = mySdk.getSdkAdditionalData();
    if (sdkData instanceof PythonRemoteSdkAdditionalData) {
      final PythonRemoteSdkAdditionalData remoteSdkData = (PythonRemoteSdkAdditionalData)sdkData;
      final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        final List<String> cmdline = new ArrayList<String>();
        cmdline.add(mySdk.getHomePath());
        cmdline.add(new RemoteFile(remoteSdkData.getPyCharmHelpersPath(),
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
                                             PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED);
      }
    }
    else {
      final String homePath = mySdk.getHomePath();
      if (homePath == null) {
        throw new PyExternalProcessException(ERROR_INVALID_SDK, helper, args, "Cannot find interpreter for SDK");
      }
      if (parentDir == null)
        parentDir = new File(homePath).getParent();
      final List<String> cmdline = new ArrayList<String>();
      cmdline.add(homePath);
      cmdline.add(helper);
      cmdline.addAll(args);

      final boolean canCreate = FileUtil.ensureCanCreateFile(new File(mySdk.getHomePath()));
      if (!canCreate && !SystemInfo.isWindows && askForSudo) {   //is system site interpreter --> we need sudo privileges
        try{
          final ProcessOutput result = ExecUtil.sudoAndGetOutput(StringUtil.join(cmdline, " "),
                                                                 "Please enter your password to make changes in system packages: ", parentDir);
          if (result.getExitCode() != 0) {
            final String stdout = result.getStdout();
            String message = result.getStderr();
            if (message.trim().isEmpty()) {
              message = stdout;
            }
            throw new PyExternalProcessException(result.getExitCode(), helper, args, message);
          }
          return result;
        }
        catch (InterruptedException e) {
          throw new PyExternalProcessException(ERROR_INTERRUPTED, helper, args, e.getMessage());
        }
        catch (ExecutionException e) {
          throw new PyExternalProcessException(ERROR_EXECUTION, helper, args, e.getMessage());
        }
        catch (ScriptException e) {
          throw new PyExternalProcessException(ERROR_TOOL_NOT_FOUND, helper, args, e.getMessage());
        }
        catch (IOException e) {
          throw new PyExternalProcessException(ERROR_ACCESS_DENIED, helper, args, e.getMessage());
        }
      }
      else                 //vEnv interpreter
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
      if (!"Python".equals(name)) {
        packages.add(new PyPackage(name, version, location, new ArrayList<PyRequirement>()));
      }
    }
    return packages;
  }
}
