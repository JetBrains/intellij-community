/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor.findInDirectory;

public class CondaEnvSdkFlavor extends CPythonSdkFlavor {
  private CondaEnvSdkFlavor() {
  }

  public final static String[] CONDA_DEFAULT_ROOTS = new String[]{"anaconda", "anaconda2", "anaconda3", "miniconda", "miniconda2",
    "miniconda3", "Anaconda", "Anaconda2", "Anaconda3", "Miniconda", "Miniconda2", "Miniconda3"};

  public static CondaEnvSdkFlavor INSTANCE = new CondaEnvSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    List<String> candidates = new ArrayList<>();

    for (VirtualFile file : getCondaDefaultLocations()) {
      candidates.addAll(findInDirectory(file));
    }

    return candidates;
  }

  public static List<VirtualFile> getCondaDefaultLocations() {
    List<VirtualFile> roots = new ArrayList<>();
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\','/'));
    if (userHome != null) {
      final VirtualFile condaHidden = userHome.findChild(".conda");
      if (condaHidden != null) {
        addEnvsFolder(roots, condaHidden);
      }
      for (String root : CONDA_DEFAULT_ROOTS) {
        VirtualFile condaFolder = userHome.findChild(root);
        addEnvsFolder(roots, condaFolder);
        if (SystemInfo.isWindows) {
          final VirtualFile appData = userHome.findFileByRelativePath("AppData\\Local\\Continuum\\" + root);
          addEnvsFolder(roots, appData);
          condaFolder = LocalFileSystem.getInstance().findFileByPath("C:\\" + root);
          addEnvsFolder(roots, condaFolder);
        }
        else {
          final String systemWidePath = "/opt/anaconda";
          condaFolder = LocalFileSystem.getInstance().findFileByPath(systemWidePath);
          addEnvsFolder(roots, condaFolder);
        }
      }
    }
    return roots;
  }

  private static void addEnvsFolder(@NotNull final List<VirtualFile> roots, @Nullable final VirtualFile condaFolder) {
    if (condaFolder != null) {
      final VirtualFile envs = condaFolder.findChild("envs");
      if (envs != null) {
        roots.add(envs);
      }
    }
  }

  @Override
  public boolean isValidSdkPath(@NotNull File file) {
    if (!super.isValidSdkPath(file)) return false;
    final File bin = file.getParentFile();
    String condaName = "conda";
    if (SystemInfo.isWindows) {
      condaName = new File(bin, "envs").exists() ? "conda.exe" : "conda.bat";
    }
    if (bin != null) {
      final File conda = new File(bin, condaName);
      if (conda.exists()) {
        return true;
      }
      final File condaFolder = bin.getParentFile();
      final File condaExecutable = PythonSdkType.findExecutableFile(condaFolder, condaName);
      if (condaExecutable != null) return true;
    }
    return false;
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Condaenv;
  }
}
