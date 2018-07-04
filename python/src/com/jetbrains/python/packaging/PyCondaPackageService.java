// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@State(name = "PyCondaPackageService", storages = @Storage(value="conda_packages.xml", roamingType = RoamingType.DISABLED))
public class PyCondaPackageService implements PersistentStateComponent<PyCondaPackageService> {
  private static final Logger LOG = Logger.getInstance(PyCondaPackageService.class);
  public Set<String> CONDA_CHANNELS = ContainerUtil.newConcurrentSet();
  public long LAST_TIME_CHECKED = 0;
  @Nullable @SystemDependent public String PREFERRED_CONDA_PATH = null;

  @Override
  public PyCondaPackageService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyCondaPackageService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static PyCondaPackageService getInstance() {
    return ServiceManager.getService(PyCondaPackageService.class);
  }

  public void loadAndGetPackages(boolean force) {
    if (PyCondaPackageCache.getInstance().getPackageNames().isEmpty() || force) {
      updatePackagesCache();
    }
  }

  public Set<String> loadAndGetChannels() {
    if (CONDA_CHANNELS.isEmpty()) {
      updateChannels();
    }
    return CONDA_CHANNELS;
  }

  public void addChannel(@NotNull final String url) {
    CONDA_CHANNELS.add(url);
  }

  public void removeChannel(@NotNull final String url) {
    if (CONDA_CHANNELS.contains(url)) {
      CONDA_CHANNELS.remove(url);
    }
  }

  @Nullable
  public static String getCondaPython() {
    final String conda = getSystemCondaExecutable();
    final String pythonName = SystemInfo.isWindows ? "python.exe" : "python";
    if (conda != null) {
      final VirtualFile condaFile = LocalFileSystem.getInstance().findFileByPath(conda);
      if (condaFile != null) {
        final VirtualFile condaDir = condaFile.getParent().getParent();
        final VirtualFile python = condaDir.findChild(pythonName);
        if (python != null) {
          return python.getPath();
        }
      }
    }
    return getCondaExecutable(pythonName);
  }

  @Nullable
  public static String getSystemCondaExecutable() {
    final String condaName = SystemInfo.isWindows ? "conda.exe" : "conda";
    final File condaInPath = PathEnvironmentVariableUtil.findInPath(condaName);
    if (condaInPath != null) return condaInPath.getPath();
    return getCondaExecutable(condaName);
  }

  @Nullable
  public static String getCondaExecutable(VirtualFile sdkPath) {
    final VirtualFile bin = sdkPath.getParent();
    String condaName = "conda";
    if (SystemInfo.isWindows) {
      condaName = bin.findChild("envs") != null ? "conda.exe" : "conda.bat";
    }
    final VirtualFile conda = bin.findChild(condaName);
    if (conda != null) return conda.getPath();
    final VirtualFile condaFolder = bin.getParent();
    final String condaPath = findExecutable(condaName, condaFolder);
    if (condaPath != null) return condaPath;
    return getSystemCondaExecutable();
  }

  @Nullable
  public static String getCondaExecutable(@NotNull final String condaName) {
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\', '/'));
    if (userHome != null) {
      for (String root : CondaEnvSdkFlavor.CONDA_DEFAULT_ROOTS) {
        VirtualFile condaFolder = userHome.findChild(root);
        String executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;
        if (SystemInfo.isWindows) {
          final VirtualFile appData = userHome.findFileByRelativePath("AppData\\Local\\Continuum\\" + root);
          executableFile = findExecutable(condaName, appData);
          if (executableFile != null) return executableFile;
          condaFolder = LocalFileSystem.getInstance().findFileByPath("C:\\" + root);
          executableFile = findExecutable(condaName, condaFolder);
          if (executableFile != null) return executableFile;
        }
        else {
          final String systemWidePath = "/opt/anaconda";
          condaFolder = LocalFileSystem.getInstance().findFileByPath(systemWidePath);
          executableFile = findExecutable(condaName, condaFolder);
          if (executableFile != null) return executableFile;
        }
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
          final String executableFile = PythonSdkType.getExecutablePath(directoryPath, condaName);
          if (executableFile != null) {
            return executableFile;
          }
        }
      }
    }
    return null;
  }

  public void updatePackagesCache() {
    final String condaPython = getCondaPython();
    if (condaPython == null) {
      return;
    }
    final String path = PythonHelpersLocator.getHelperPath("conda_packaging_tool.py");
    final String runDirectory = new File(condaPython).getParent();
    final String[] command = {condaPython, path, "listall"};
    final ProcessOutput output = PySdkUtil.getProcessOutput(runDirectory, command);
    if (output.getExitCode() != 0) {
      LOG.warn("Failed to get list of conda packages");
      LOG.warn(StringUtil.join(command, " "));
      LOG.warn(output.getStderr());
      return;
    }

    final Multimap<String, String> nameToVersions =
      Multimaps.newSortedSetMultimap(new HashMap<>(), () -> new TreeSet<>(VersionComparatorUtil.COMPARATOR.reversed()));
    for (String line : output.getStdoutLines()) {
      final List<String> split = StringUtil.split(line, "\t");
      if (split.size() < 2) continue;
      nameToVersions.put(split.get(0), split.get(1));
    }
    PyCondaPackageCache.reload(nameToVersions);
    LAST_TIME_CHECKED = System.currentTimeMillis();
  }

  @NotNull
  public List<String> getPackageVersions(@NotNull final String packageName) {
    return ContainerUtil.notNullize(PyCondaPackageCache.getInstance().getVersions(packageName));
  }

  public void updateChannels() {
    final String condaPython = getCondaPython();
    if (condaPython == null) return;
    final String path = PythonHelpersLocator.getHelperPath("conda_packaging_tool.py");
    final String runDirectory = new File(condaPython).getParent();
    final ProcessOutput output = PySdkUtil.getProcessOutput(runDirectory, new String[]{condaPython, path, "channels"});
    if (output.getExitCode() != 0) return;
    final List<String> lines = output.getStdoutLines();
    CONDA_CHANNELS.addAll(lines);
    LAST_TIME_CHECKED = System.currentTimeMillis();
  }
}
