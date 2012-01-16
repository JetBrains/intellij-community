package com.jetbrains.python.packaging;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.SdkUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackagingUtil {
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
  private static final Logger LOG = Logger.getInstance("#" + PyPackagingUtil.class.getName());
  private static PyPackagingUtil ourInstance = null;

  private Map<Sdk, List<PyPackage>> myPackagesCache = new HashMap<Sdk, List<PyPackage>>();

  private PyPackagingUtil() {
  }

  public void uninstallPackage(@NotNull Sdk sdk, @NotNull PyPackage pkg) throws PyExternalProcessException {
    runPythonHelper(sdk, PACKAGING_TOOL, list("uninstall", pkg.getName()));
    myPackagesCache.remove(sdk);
  }

  @NotNull
  public static String createVirtualEnv(@NotNull Sdk sdk, @NotNull String desinationDir) throws PyExternalProcessException {
    // TODO: Add boolean systemSitePackages option
    runPythonHelper(sdk, VIRTUALENV, list("--never-download", "--distribute", desinationDir));
    final String binary = PythonSdkType.getPythonExecutable(desinationDir);
    final String binaryFallback = desinationDir + File.separator + "bin" + File.separator + "python";
    return (binary != null) ? binary : binaryFallback;
  }

  public static void deleteVirtualEnv(@NotNull Sdk sdk, @NotNull String sdkHome) throws PyExternalProcessException {
    final File root = PythonSdkType.getVirtualEnvRoot(sdkHome);
    if (root == null) {
      throw new PyExternalProcessException(ERROR_INVALID_SDK, "Cannot find virtualenv root for interpreter");
    }
    FileUtil.delete(root);
  }

  @NotNull
  public static PyPackagingUtil getInstance() {
    if (ourInstance == null) {
      ourInstance = new PyPackagingUtil();
    }
    return ourInstance;
  }

  @NotNull
  public List<PyPackage> getInstalledPackages(@NotNull Sdk sdk) throws PyExternalProcessException {
    final List<PyPackage> cached = myPackagesCache.get(sdk);
    if (cached != null) {
      return cached;
    }
    final String output = runPythonHelper(sdk, PACKAGING_TOOL, list("list"));
    final List<PyPackage> packages = parsePackagingToolOutput(output);
    myPackagesCache.put(sdk, packages);
    return packages;
  }

  public void clearCaches() {
    myPackagesCache.clear();
  }

  private static <T> List<T> list(T... xs) {
    return Arrays.asList(xs);
  }

  @NotNull
  private static String runPythonHelper(@NotNull Sdk sdk,
                                        @NotNull String helper,
                                        @NotNull List<String> args) throws PyExternalProcessException {
    final String homePath = sdk.getHomePath();
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
      packages.add(new PyPackage(name, version, location, new ArrayList<PyRequirement>()));
    }
    return packages;
  }
}
