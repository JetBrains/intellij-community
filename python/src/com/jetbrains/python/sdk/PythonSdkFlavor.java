package com.jetbrains.python.sdk;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public abstract class PythonSdkFlavor {
  private static final Logger LOG = Logger.getInstance(PythonSdkFlavor.class);

  public static Collection<String> appendSystemPythonPath(Collection<String> pythonPath) {
    String syspath = System.getenv(PythonEnvUtil.PYTHONPATH);
    if (syspath != null) {
      pythonPath.addAll(Lists.newArrayList(syspath.split(File.pathSeparator)));
    }
    return pythonPath;
  }


  public static void initPythonPath(Map<String, String> envs, boolean passParentEnvs, Collection<String> pythonPathList) {
    if (passParentEnvs && !envs.containsKey(PythonEnvUtil.PYTHONPATH)) {
      pythonPathList = appendSystemPythonPath(pythonPathList);
    }
    PythonEnvUtil.addToPythonPath(envs, pythonPathList);
  }

  public Collection<String> suggestHomePaths() {
    return Collections.emptyList();
  }

  public static List<PythonSdkFlavor> getApplicableFlavors() {
    List<PythonSdkFlavor> result = new ArrayList<PythonSdkFlavor>();

    if (SystemInfo.isWindows) {
      result.add(WinPythonSdkFlavor.INSTANCE);
    }
    else if (SystemInfo.isMac) {
      result.add(MacPythonSdkFlavor.INSTANCE);
    }
    else if (SystemInfo.isUnix) {
      result.add(UnixPythonSdkFlavor.INSTANCE);
    }

    result.addAll(getPlatformIndependentFlavors());

    return result;
  }


  public static List<PythonSdkFlavor> getPlatformIndependentFlavors() {
    List<PythonSdkFlavor> result = Lists.newArrayList();
    result.add(JythonSdkFlavor.INSTANCE);
    result.add(IronPythonSdkFlavor.INSTANCE);
    result.add(PyPySdkFlavor.INSTANCE);
    result.add(VirtualEnvSdkFlavor.INSTANCE);
    result.add(PyRemoteSdkFlavor.INSTANCE);

    return result;
  }

  @Nullable
  public static PythonSdkFlavor getFlavor(Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof PythonSdkAdditionalData) {
      PythonSdkFlavor flavor = ((PythonSdkAdditionalData)data).getFlavor();
      if (flavor != null) {
        return flavor;
      }
    }
    return getFlavor(sdk.getHomePath());
  }

  @Nullable
  public static PythonSdkFlavor getFlavor(String sdkPath) {
    if (sdkPath == null) return null;

    for (PythonSdkFlavor flavor : getApplicableFlavors()) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  @Nullable
  public static PythonSdkFlavor getPlatformIndependentFlavor(@Nullable final String sdkPath) {
    if (sdkPath == null) return null;

    for (PythonSdkFlavor flavor : getPlatformIndependentFlavors()) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  /**
   * Checks if the path is the name of a Python interpreter of this flavor.
   *
   * @param path path to check.
   * @return true if paths points to a valid home.
   */
  public boolean isValidSdkHome(String path) {
    File file = new File(path);
    return file.isFile() && isValidSdkPath(file);
  }

  public boolean isValidSdkPath(@NotNull File file) {
    return FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("python");
  }

  public String getVersionString(String sdkHome) {
    return getVersionStringFromOutput(getVersionFromOutput(sdkHome, getVersionOption(), getVersionRegexp()));
  }

  public String getVersionStringFromOutput(String version) {
    return version;
  }


  public String getVersionRegexp() {
    return "(Python \\S+).*";
  }

  public String getVersionOption() {
    return "-V";
  }

  @Nullable
  public String getVersionFromOutput(ProcessOutput processOutput) {
    return getVersionFromOutput(getVersionRegexp(), processOutput);
  }

  @Nullable
  protected static String getVersionFromOutput(String sdkHome, String version_opt, String version_regexp) {
    String run_dir = new File(sdkHome).getParent();
    final ProcessOutput process_output = PySdkUtil.getProcessOutput(run_dir, new String[]{sdkHome, version_opt});

    return getVersionFromOutput(version_regexp, process_output);
  }

  @Nullable
  private static String getVersionFromOutput(String version_regexp, ProcessOutput process_output) {
    if (process_output.getExitCode() != 0) {
      String err = process_output.getStderr();
      if (StringUtil.isEmpty(err)) {
        err = process_output.getStdout();
      }
      LOG.warn("Couldn't get interpreter version: process exited with code " + process_output.getExitCode() + "\n" + err
      );
      return null;
    }
    Pattern pattern = Pattern.compile(version_regexp);
    final String result = PySdkUtil.getFirstMatch(process_output.getStderrLines(), pattern);
    if (result != null) {
      return result;
    }
    return PySdkUtil.getFirstMatch(process_output.getStdoutLines(), pattern);
  }

  public Collection<String> getExtraDebugOptions() {
    return Collections.emptyList();
  }

  public void initPythonPath(GeneralCommandLine cmd, Collection<String> path) {
    addToEnv(cmd, PythonEnvUtil.PYTHONPATH, appendSystemPythonPath(path));
  }

  public static void addToEnv(GeneralCommandLine cmd, final String key, Collection<String> values) {
    Map<String, String> envs = getEnv(cmd);
    PythonEnvUtil.addToEnv(envs, key, values);
  }

  public static void addToEnv(GeneralCommandLine cmd, final String key, String value) {
    Map<String, String> envs = getEnv(cmd);
    PythonEnvUtil.addToEnv(envs, key, value);
  }

  private static Map<String, String> getEnv(GeneralCommandLine cmd) {
    Map<String, String> envs = cmd.getEnvParams();
    if (envs == null) {
      envs = new HashMap<String, String>();
      cmd.setEnvParams(envs);
    }
    return envs;
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  public void addPredefinedEnvironmentVariables(Map<String, String> envs) {
    Charset defaultCharset = EncodingManager.getInstance().getDefaultCharset();
    if (defaultCharset != null) {
      final String encoding = defaultCharset.name();
      PythonEnvUtil.setPythonIOEncoding(envs, encoding);
    }
  }

  @NotNull
  public abstract String getName();

  public LanguageLevel getLanguageLevel(Sdk sdk) {
    final String version = sdk.getVersionString();
    final String prefix = getName() + " ";
    if (version != null && version.startsWith(prefix)) {
      return LanguageLevel.fromPythonVersion(version.substring(prefix.length()));
    }
    return LanguageLevel.getDefault();
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/com/jetbrains/python/icons/python.png");
  }
}
