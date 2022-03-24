// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.flavors.PyCondaRunKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;

import java.io.File;
import java.util.*;

@State(name = "PyCondaPackageService", storages = @Storage(value="conda_packages.xml", roamingType = RoamingType.DISABLED))
public class PyCondaPackageService implements PersistentStateComponent<PyCondaPackageService> {
  private static final Logger LOG = Logger.getInstance(PyCondaPackageService.class);

  private final static String[] CONDA_DEFAULT_ROOTS =
    new String[]{"anaconda", "anaconda3", "miniconda", "miniconda3", "Anaconda", "Anaconda3", "Miniconda", "Miniconda3"};

  private static final String CONDA_ENVS_DIR = "envs";

  @Nullable @SystemDependent @Property private String PREFERRED_CONDA_PATH = null;

  @Override
  public PyCondaPackageService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyCondaPackageService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static PyCondaPackageService getInstance() {
    return ApplicationManager.getApplication().getService(PyCondaPackageService.class);
  }

  @Nullable
  private static String getCondaPython() {
    final String conda = getCondaExecutable(null);
    if (conda != null) {
      final String python = getCondaBasePython(conda);
      if (python != null) return python;
    }
    return getCondaExecutableByName(getPythonName());
  }

  @Nullable
  public static String getCondaBasePython(@NotNull String systemCondaExecutable) {
    final VirtualFile condaFile = LocalFileSystem.getInstance().findFileByPath(systemCondaExecutable);
    if (condaFile != null) {
      final VirtualFile condaDir = SystemInfo.isWindows ? condaFile.getParent().getParent() : condaFile.getParent();
      final VirtualFile python = condaDir.findChild(getPythonName());
      if (python != null) {
        return python.getPath();
      }
    }
    return null;
  }

  @NotNull
  private static String getPythonName() {
    return SystemInfo.isWindows ? "python.exe" : "python";
  }

  @Nullable
  private static String getSystemCondaExecutable() {
    final String condaName = SystemInfo.isWindows ? "conda.exe" : "conda";

    final File condaInPath = PathEnvironmentVariableUtil.findInPath(condaName);
    if (condaInPath != null) {
      LOG.info("Using " + condaInPath + " as a conda executable (found in PATH)");
      return condaInPath.getPath();
    }

    final String condaInRoots = getCondaExecutableByName(condaName);
    if (condaInRoots != null) {
      LOG.info("Using " + condaInRoots + " as a conda executable (found by visiting possible conda roots)");
      return condaInRoots;
    }

    LOG.info("System conda executable is not found");
    return null;
  }

  @Nullable
  @SystemDependent
  public static String getCondaExecutable(@Nullable String sdkPath) {
    if (sdkPath != null) {
      String condaPath = findCondaExecutableRelativeToEnv(sdkPath);
      if (condaPath != null) {
        LOG.info("Using " + condaPath + " as a conda executable for " + sdkPath + " (found as a relative to the env)");
        return condaPath;
      }
    }

    final String preferredCondaPath = getInstance().PREFERRED_CONDA_PATH;
    if (StringUtil.isNotEmpty(preferredCondaPath)) {
      final String forSdkPath = sdkPath == null ? "" : " for " + sdkPath;
      LOG.info("Using " + preferredCondaPath + " as a conda executable" + forSdkPath + " (specified as a preferred conda path)");
      return preferredCondaPath;
    }

    return getSystemCondaExecutable();
  }

  public static void onCondaEnvCreated(@NotNull @SystemDependent String condaExecutable) {
    getInstance().PREFERRED_CONDA_PATH = condaExecutable;
  }

  @Nullable
  private static String findCondaExecutableRelativeToEnv(@NotNull String sdkPath) {
    final VirtualFile pyExecutable = StandardFileSystems.local().findFileByPath(sdkPath);
    if (pyExecutable == null) {
      return null;
    }
    final VirtualFile pyExecutableDir = pyExecutable.getParent();
    final boolean isBaseConda = pyExecutableDir.findChild(CONDA_ENVS_DIR) != null;
    final String condaName;
    final VirtualFile condaFolder;
    if (SystemInfo.isWindows) {
      condaName = "conda.exe";
      // On Windows python.exe is directly inside base interpreter/environment directory.
      // On other systems executable normally resides in "bin" subdirectory.
      condaFolder = pyExecutableDir;
    }
    else {
      condaName = "conda";
      condaFolder = pyExecutableDir.getParent();
    }

    // XXX Do we still need to support this? When did they drop per-environment conda executable?
    final String localCondaName = SystemInfo.isWindows && !isBaseConda ? "conda.bat" : condaName;
    final String immediateConda = findExecutable(localCondaName, condaFolder);
    if (immediateConda != null) {
      return immediateConda;
    }
    final VirtualFile envsDir = condaFolder.getParent();
    if (!isBaseConda && envsDir != null && envsDir.getName().equals(CONDA_ENVS_DIR)) {
      return findExecutable(condaName, envsDir.getParent());
    }
    return null;
  }

  @Nullable
  private static String getCondaExecutableByName(@NotNull final String condaName) {
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\', '/'));

    for (String root : CONDA_DEFAULT_ROOTS) {
      VirtualFile condaFolder = userHome == null ? null : userHome.findChild(root);
      String executableFile = findExecutable(condaName, condaFolder);
      if (executableFile != null) return executableFile;

      //noinspection IfStatementWithIdenticalBranches
      if (SystemInfo.isWindows) {
        condaFolder = userHome == null ? null : userHome.findFileByRelativePath("AppData\\Local\\Continuum\\" + root);
        executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;

        condaFolder = LocalFileSystem.getInstance().findFileByPath("C:\\ProgramData\\" + root);
        executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;

        condaFolder = LocalFileSystem.getInstance().findFileByPath("C:\\" + root);
        executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;
      }
      else {
        condaFolder = LocalFileSystem.getInstance().findFileByPath("/opt/" + root);
        executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;
      }
    }

    return null;
  }

  @Nullable
  private static String findExecutable(String condaName, @Nullable final VirtualFile condaFolder) {
    if (condaFolder != null) {
      final VirtualFile binFolder = condaFolder.findChild(SystemInfo.isWindows ? "Scripts" : "bin");
      if (binFolder != null) {
        final VirtualFile bin = binFolder.findChild(condaName);
        if (bin != null) {
          String directoryPath = bin.getPath();
          final String executableFile = PythonSdkUtil.getExecutablePath(directoryPath, condaName);
          if (executableFile != null) {
            return executableFile;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static Multimap<String, String> listAllPackagesAndVersions() {
    try {
      final String output = runCondaPackagingHelper("listall");
      final Multimap<String, String> nameToVersions =
        Multimaps.newSortedSetMultimap(new HashMap<>(), () -> new TreeSet<>(PyPackageVersionComparator.getSTR_COMPARATOR().reversed()));
      for (String line : StringUtil.split(output, "\n")) {
        final List<String> split = StringUtil.split(line, "\t");
        if (split.size() < 2) continue;
        nameToVersions.put(split.get(0), split.get(1));
      }
      return nameToVersions;
    }
    catch (ExecutionException e) {
      LOG.warn("Failed to get list of conda packages. " + e);
      return null;
    }
  }

  @NotNull
  public static List<String> listPackageVersions(@NotNull String packageName) throws ExecutionException {
    final String output = runCondaPackagingHelper("versions", packageName);
    return StringUtil.split(output, "\n");
  }

  @NotNull
  public static List<String> listChannels() throws ExecutionException {
    final String output = runCondaPackagingHelper("channels");
    return StringUtil.split(output, "\n");
  }

  @NotNull
  private static String runCondaPackagingHelper(String @NotNull ... args) throws ExecutionException {
    final List<String> commandArgs = new ArrayList<>();
    commandArgs.add(PythonHelpersLocator.getHelperPath("conda_packaging_tool.py"));
    commandArgs.addAll(Arrays.asList(args));
    // "conda" module required for conda_packaging_tool.py is available only in a base interpreter
    final String condaPython = getCondaPython();
    if (condaPython == null) {
      throw new PyExecutionException(PySdkBundle.message("python.conda.cannot.find.python.executable"),
                                     "python", commandArgs, new ProcessOutput());
    }
    final ProcessOutput output = PyCondaRunKt.runCondaPython(condaPython, commandArgs);
    return output.getStdout();
  }
}
