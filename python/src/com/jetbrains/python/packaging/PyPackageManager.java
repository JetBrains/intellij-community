package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.SdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackageManager {
  public final static int OK = 0;
  public final static int ERROR_WRONG_USAGE = 1;
  public final static int ERROR_NO_PACKAGING_TOOLS = 2;
  public final static int ERROR_INVALID_SDK = -1;
  public final static int ERROR_HELPER_NOT_FOUND = -2;
  public final static int ERROR_TIMEOUT = -3;
  public final static int ERROR_INVALID_OUTPUT = -4;

  private static final String PACKAGING_TOOL = "packaging_tool.py";
  private static final String VIRTUALENV = "virtualenv.py";
  private static final int TIMEOUT = 10 * 60 * 1000;
  private static final Logger LOG = Logger.getInstance("#" + PyPackageManager.class.getName());

  private static final Map<Sdk, PyPackageManager> ourInstances = new HashMap<Sdk, PyPackageManager>();

  private List<PyPackage> myPackagesCache = null;
  private Sdk mySdk;

  private PyPackageManager(@NotNull Sdk sdk) {
    mySdk = sdk;
  }

  @NotNull
  public static PyPackageManager getInstance(@NotNull Sdk sdk) {
    PyPackageManager manager = ourInstances.get(sdk);
    if (manager == null) {
      manager = new PyPackageManager(sdk);
      ourInstances.put(sdk, manager);
    }
    return manager;
  }

  public Sdk getSdk() {
    return mySdk;
  }

  public void install(@NotNull List<PyRequirement> requirements) throws PyExternalProcessException {
    install(requirements, null);
  }

  public void install(@NotNull PyRequirement requirement) throws PyExternalProcessException {
    install(requirement, null);
  }

  public void install(@NotNull PyRequirement requirement, @Nullable List<String> options) throws PyExternalProcessException {
    install(Lists.newArrayList(requirement), options);
  }

  public void install(@NotNull PyRequirement requirement, @Nullable String url,
                      @Nullable List<String> options) throws PyExternalProcessException {
    install(Lists.newArrayList(requirement), url, options);
  }

  public void install(@NotNull List<PyRequirement> requirements, @Nullable String url,
                      @Nullable List<String> options) throws PyExternalProcessException {
    myPackagesCache = null;
    final List<String> args = new ArrayList<String>();
    args.add("install");
    if (url != null) {
      args.add("--extra-index-url");
      args.add(url);
    }
    for (PyRequirement req : requirements) {
      args.add(req.toString());
    }
    if (options != null)
      args.addAll(options);
    runPythonHelper(PACKAGING_TOOL, args);
  }

  public void install(@NotNull List<PyRequirement> requirements, @Nullable List<String> options) throws PyExternalProcessException {
    install(requirements, null, options);
  }

  @Deprecated
  public void install(@NotNull PyPackage pkg) throws PyExternalProcessException {
    // TODO: Add options for mirrors, web pages with indices, upgrade flag, package file path
    myPackagesCache = null;
    runPythonHelper(PACKAGING_TOOL, list("install", pkg.getName()));
  }

  public void uninstall(@NotNull PyPackage pkg) throws PyExternalProcessException {
    myPackagesCache = null;
    runPythonHelper(PACKAGING_TOOL, list("uninstall", pkg.getName()));
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

  @NotNull
  public String createVirtualEnv(@NotNull String desinationDir) throws PyExternalProcessException {
    // TODO: Add boolean systemSitePackages option
    runPythonHelper(VIRTUALENV, list("--never-download", "--distribute", desinationDir));
    final String binary = PythonSdkType.getPythonExecutable(desinationDir);
    final String binaryFallback = desinationDir + File.separator + "bin" + File.separator + "python";
    return (binary != null) ? binary : binaryFallback;
  }

  public static void deleteVirtualEnv(@NotNull String sdkHome) throws PyExternalProcessException {
    final File root = PythonSdkType.getVirtualEnvRoot(sdkHome);
    if (root == null) {
      throw new PyExternalProcessException(ERROR_INVALID_SDK, "Cannot find virtualenv root for interpreter");
    }
    FileUtil.delete(root);
  }

  public void clearCaches() {
    myPackagesCache = null;
  }

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
  }

  @NotNull
  private String runPythonHelper(@NotNull String helper,
                                 @NotNull List<String> args) throws PyExternalProcessException {
    final String homePath = mySdk.getHomePath();
    if (homePath == null) {
      throw new PyExternalProcessException(ERROR_INVALID_SDK, "Cannot find interpreter for SDK");
    }
    final String helperPath = PythonHelpersLocator.getHelperPath(helper);
    if (helperPath == null) {
      throw new PyExternalProcessException(ERROR_HELPER_NOT_FOUND, String.format("Cannot find helper tool: '%s'", helper));
    }
    final String parentDir = new File(homePath).getParent();
    final List<String> cmdline = new ArrayList<String>();
    cmdline.add(homePath);
    cmdline.add(helperPath);
    cmdline.addAll(args);
    ProcessOutput output = SdkUtil.getProcessOutput(parentDir, cmdline.toArray(new String[cmdline.size()]), TIMEOUT);
    final int retcode = output.getExitCode();
    if (output.isTimeout()) {
      throw new PyExternalProcessException(ERROR_TIMEOUT, "Timed out");
    }
    else if (retcode != 0) {
      final String stdout = output.getStdout();
      final String stderr = output.getStderr();
      String message = stderr;
      if (message.trim().isEmpty()) {
        message = stdout;
      }
      LOG.debug(String.format("Error when running '%s'\nSTDOUT: %s\nSTDERR: %s\n\n",
                              StringUtil.join(cmdline, " "),
                              stdout,
                              stderr));
      throw new PyExternalProcessException(retcode, message);
    }
    return output.getStdout();
  }

  @NotNull
  private static List<PyPackage> parsePackagingToolOutput(@NotNull String s) throws PyExternalProcessException {
    final String[] lines = StringUtil.splitByLines(s);
    final List<PyPackage> packages = new ArrayList<PyPackage>();
    for (String line : lines) {
      final List<String> fields = StringUtil.split(line, "\t");
      if (fields.size() < 3) {
        throw new PyExternalProcessException(ERROR_INVALID_OUTPUT, String.format("Invalid output format of '%s'", PACKAGING_TOOL));
      }
      final String name = fields.get(0);
      final String version = fields.get(1);
      final String location = fields.get(2);
      if (!"Python".equals(name) && !"wsgiref".equals(name))
        packages.add(new PyPackage(name, version, location, new ArrayList<PyRequirement>()));
    }
    return packages;
  }
}
