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
package com.jetbrains.python.sdk.skeletons;

import com.google.common.collect.Maps;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.jetbrains.python.sdk.skeletons.SkeletonVersionChecker.fromVersionString;

/**
 * @author traff
 */
public class PySkeletonGenerator {
  protected static final Logger LOG = Logger.getInstance("#" + PySkeletonGenerator.class.getName());

  protected final static int MINUTE = 60 * 1000;

  protected final static String GENERATOR3 = "generator3.py";

  private final String mySkeletonsPath;

  public void finishSkeletonsGeneration() {
  }

  public boolean exists(String name) {
    return new File(name).exists();
  }

  public static class ListBinariesResult {
    public final int generatorVersion;
    public final Map<String, PySkeletonRefresher.PyBinaryItem> modules;

    public ListBinariesResult(int generatorVersion, Map<String, PySkeletonRefresher.PyBinaryItem> modules) {
      this.generatorVersion = generatorVersion;
      this.modules = modules;
    }
  }

  public PySkeletonGenerator(String skeletonPath) {
    mySkeletonsPath = skeletonPath;
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

    if (genResult.getStderrLines().size() > 0) {
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
      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
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
    List<String> commandLine = new ArrayList<String>();
    commandLine.add(binaryPath);
    commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
    commandLine.add("-d");
    commandLine.add(getSkeletonsPath());
    if (assemblyRefs != null && !assemblyRefs.isEmpty()) {
      commandLine.add("-c");
      commandLine.add(StringUtil.join(assemblyRefs, ";"));
    }
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
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

    return getProcessOutput(parent_dir, ArrayUtil.toStringArray(commandLine), PythonSdkType.getVirtualEnvAdditionalEnv(binaryPath),
                            MINUTE * 10
    );
  }

  protected ProcessOutput getProcessOutput(String homePath, String[] commandLine, String[] env, int timeout) throws InvalidSdkException {
    return PySdkUtil.getProcessOutput(
      homePath,
      commandLine,
      env,
      timeout
    );
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
      PythonSdkType.getVirtualEnvAdditionalEnv(binaryPath), MINUTE * 5
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

    List<String> cmd = new ArrayList<String>(Arrays.asList(homePath, PythonHelpersLocator.getHelperPath(GENERATOR3), "-v", "-L"));
    if (!StringUtil.isEmpty(extraSysPath)) {
      cmd.add("-s");
      cmd.add(extraSysPath);
    }

    final ProcessOutput process = getProcessOutput(parentDir,
                                                   ArrayUtil.toStringArray(cmd),
                                                   PythonSdkType.getVirtualEnvAdditionalEnv(homePath),
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
