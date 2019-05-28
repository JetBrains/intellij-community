// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.IronPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.jetbrains.python.sdk.skeletons.SkeletonVersionChecker.fromVersionString;

/**
 * @author traff
 */
public class PySkeletonGenerator {

  // Some flavors need current folder to be passed as param. Here are they.
  private static final Map<Class<? extends PythonSdkFlavor>, String> ENV_PATH_PARAM =
    new HashMap<>();

  static {
    ENV_PATH_PARAM.put(IronPythonSdkFlavor.class, "IRONPYTHONPATH"); // TODO: Make strategy and move to PythonSdkFlavor?
  }

  protected static final Logger LOG = Logger.getInstance(PySkeletonGenerator.class);
  protected static final int MINUTE = 60 * 1000;
  protected static final String GENERATOR3 = "generator3.py";

  private final String mySkeletonsPath;
  @NotNull protected final Map<String, String> myEnv;

  private boolean myPrebuilt = false;

  public void finishSkeletonsGeneration() {
  }

  public boolean exists(@NotNull final String name) {
    return new File(name).exists();
  }

  public void setPrebuilt(boolean prebuilt) {
    myPrebuilt = prebuilt;
  }

  public static class ListBinariesResult {
    public final int generatorVersion;
    public final Map<String, PySkeletonRefresher.PyBinaryItem> modules;

    public ListBinariesResult(int generatorVersion, Map<String, PySkeletonRefresher.PyBinaryItem> modules) {
      this.generatorVersion = generatorVersion;
      this.modules = modules;
    }
  }

  /**
   * @param skeletonPath path where skeletons should be generated
   * @param pySdk SDK
   * @param currentFolder current folder (some flavors may search for binary files there) or null if unknown
   */
  public PySkeletonGenerator(String skeletonPath, @NotNull final Sdk pySdk, @Nullable final String currentFolder) {
    mySkeletonsPath = skeletonPath;
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(pySdk);
    if (currentFolder != null && flavor != null && ENV_PATH_PARAM.containsKey(flavor.getClass())) {
      myEnv = ImmutableMap.of(ENV_PATH_PARAM.get(flavor.getClass()), currentFolder);
    }
    else {
      myEnv = Collections.emptyMap();
    }
  }

  public String getSkeletonsPath() {
    return mySkeletonsPath;
  }

  public void prepare() {
  }

  protected void generateSkeleton(String modname,
                                  String modfilename,
                                  List<String> assemblyRefs,
                                  String syspath,
                                  String sdkHomePath,
                                  Consumer<Boolean> resultConsumer)
    throws InvalidSdkException {

    final ProcessOutput genResult = runSkeletonGeneration(modname, modfilename, assemblyRefs, sdkHomePath,
                                                          syspath);

    final Application app = ApplicationManager.getApplication();
    if (app.isInternal() || app.isEAP()) {
      final String stdout = genResult.getStdout();
      if (StringUtil.isNotEmpty(stdout)) {
        LOG.info(stdout);
      }
    }
    if (!genResult.getStderrLines().isEmpty()) {
      StringBuilder sb = new StringBuilder("Skeleton for ");
      sb.append(modname);
      if (genResult.getExitCode() != 0) {
        sb.append(" failed on ");
      }
      else {
        sb.append(" had some minor errors on ");
      }
      sb.append(sdkHomePath).append(". stderr: --\n");
      for (String err_line : genResult.getStderrLines()) {
        sb.append(err_line).append("\n");
      }
      sb.append("--");
      if (app.isInternal()) {
        LOG.warn(sb.toString());
      }
      else {
        LOG.info(sb.toString());
      }
    }

    resultConsumer.consume(genResult.getExitCode() == 0);
  }

  public ProcessOutput runSkeletonGeneration(String modname,
                                             String modfilename,
                                             List<String> assemblyRefs,
                                             String binaryPath, String extraSyspath)
    throws InvalidSdkException {
    final String parent_dir = new File(binaryPath).getParent();
    List<String> commandLine = buildSkeletonGeneratorCommandLine(modname, modfilename, assemblyRefs, binaryPath, extraSyspath);

    final Map<String, String> extraEnv = PythonSdkType.activateVirtualEnv(binaryPath);
    final Map<String, String> env = new HashMap<>(!extraEnv.isEmpty() ? PySdkUtil.mergeEnvVariables(myEnv, extraEnv) : myEnv);

    if (myPrebuilt) {
      env.put("IS_PREGENERATED_SKELETONS", "1");
    }

    return getProcessOutput(parent_dir, ArrayUtilRt.toStringArray(commandLine), env, MINUTE * 10);
  }

  @NotNull
  protected final List<String> buildSkeletonGeneratorCommandLine(@NotNull String modname,
                                                                 @Nullable String modfilename,
                                                                 @Nullable List<String> assemblyRefs,
                                                                 @NotNull String binaryPath,
                                                                 @Nullable String extraSyspath) {
    List<String> commandLine = new ArrayList<>();
    commandLine.add(binaryPath);
    commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
    commandLine.add("-d");
    commandLine.add(getSkeletonsPath());
    if (assemblyRefs != null && !assemblyRefs.isEmpty()) {
      commandLine.add("-c");
      commandLine.add(StringUtil.join(assemblyRefs, ";"));
    }
    if (ApplicationManager.getApplication().isInternal()) {
      commandLine.add("-x");
    }
    if (!StringUtil.isEmpty(extraSyspath)) {
      commandLine.add("-s");
      commandLine.add(extraSyspath);
    }
    commandLine.add(modname);
    if (modfilename != null) {
      commandLine.add(modfilename);
    }
    return commandLine;
  }

  protected ProcessOutput getProcessOutput(String homePath, @NotNull String[] commandLine, Map<String, String> extraEnv,
                                           int timeout) throws InvalidSdkException {
    final Map<String, String> env = extraEnv != null ? new HashMap<>(extraEnv) : new HashMap<>();
    PythonEnvUtil.setPythonDontWriteBytecode(env);
    return PySdkUtil.getProcessOutput(homePath, commandLine, env, timeout);
  }

  public void generateBuiltinSkeletons(@NotNull Sdk sdk) throws InvalidSdkException {
    //noinspection ResultOfMethodCallIgnored
    new File(mySkeletonsPath).mkdirs();
    String binaryPath = sdk.getHomePath();
    if (binaryPath == null) throw new InvalidSdkException("Broken home path for " + sdk.getName());

    long startTime = System.currentTimeMillis();
    final ProcessOutput runResult = getProcessOutput(
      new File(binaryPath).getParent(),
      new String[]{
        binaryPath,
        PythonHelpersLocator.getHelperPath(GENERATOR3),
        "-d", mySkeletonsPath, // output dir
        "-b", // for builtins
      },
      PythonSdkType.activateVirtualEnv(sdk), MINUTE * 5
    );
    runResult.checkSuccess(LOG);
    LOG.info("Rebuilding builtin skeletons took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  @NotNull
  public ListBinariesResult listBinaries(@NotNull Sdk sdk, @NotNull String extraSysPath) throws InvalidSdkException {
    final String homePath = sdk.getHomePath();
    final long startTime = System.currentTimeMillis();
    if (homePath == null) throw new InvalidSdkException("Broken home path for " + sdk.getName());
    final String parentDir = new File(homePath).getParent();

    List<String> cmd = new ArrayList<>(Arrays.asList(homePath, PythonHelpersLocator.getHelperPath(GENERATOR3), "-L"));
    if (!StringUtil.isEmpty(extraSysPath)) {
      cmd.add("-s");
      cmd.add(extraSysPath);
    }

    final ProcessOutput process = getProcessOutput(parentDir,
                                                   ArrayUtilRt.toStringArray(cmd),
                                                   PythonSdkType.activateVirtualEnv(sdk),
                                                   MINUTE * 4); // see PY-3898

    LOG.info("Retrieving binary module list took " + (System.currentTimeMillis() - startTime) + " ms");
    if (process.getExitCode() != 0) {
      final StringBuilder sb = new StringBuilder("failed to run ").append(GENERATOR3).append(" for ").append(homePath);
      if (process.isTimeout()) {
        sb.append(": timed out.");
      }
      else {
        sb.append(", exit code ")
          .append(process.getExitCode())
          .append(", stderr: \n-----\n");
        for (String line : process.getStderrLines()) {
          sb.append(line).append("\n");
        }
        sb.append("-----");
      }
      throw new InvalidSdkException(sb.toString());
    }
    final List<String> lines = process.getStdoutLines();
    if (lines.size() < 1) {
      throw new InvalidSdkException("Empty output from " + GENERATOR3 + " for " + homePath);
    }
    final Iterator<String> iter = lines.iterator();
    final int generatorVersion = fromVersionString(iter.next().trim());
    final Map<String, PySkeletonRefresher.PyBinaryItem> binaries = Maps.newHashMap();
    while (iter.hasNext()) {
      final String line = iter.next();
      int cutpos = line.indexOf('\t');
      if (cutpos >= 0) {
        String[] strs = line.split("\t");
        String moduleName = strs[0];
        String path = strs[1];
        int length = Integer.parseInt(strs[2]);
        int lastModified = Integer.parseInt(strs[3]);

        binaries.put(moduleName, new PySkeletonRefresher.PyBinaryItem(moduleName, path, length, lastModified));
      }
      else {
        LOG.error("Bad binaries line: '" + line + "', SDK " + homePath); // but don't die yet
      }
    }
    return new ListBinariesResult(generatorVersion, binaries);
  }

  public boolean deleteOrLog(@NotNull File item) {
    boolean deleted = item.delete();
    if (!deleted) LOG.warn("Failed to delete skeleton file " + item.getAbsolutePath());
    return deleted;
  }

  public void refreshGeneratedSkeletons() {
    VirtualFile skeletonsVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(getSkeletonsPath());
    assert skeletonsVFile != null;
    skeletonsVFile.refresh(false, true);
  }
}
