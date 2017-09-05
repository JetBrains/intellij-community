/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
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

/**
 * User : catherine
 */
public class VirtualEnvSdkFlavor extends CPythonSdkFlavor {
  private VirtualEnvSdkFlavor() {
  }
  private final static String[] NAMES = new String[]{"jython", "pypy", "python.exe", "jython.bat", "pypy.exe"};
  public final static String[] CONDA_DEFAULT_ROOTS = new String[]{"anaconda", "anaconda2", "anaconda3", "miniconda", "miniconda2",
    "miniconda3", "Anaconda", "Anaconda2", "Anaconda3", "Miniconda", "Miniconda2", "Miniconda3"};

  public static VirtualEnvSdkFlavor INSTANCE = new VirtualEnvSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    List<String> candidates = new ArrayList<>();
    if (project != null) {
      VirtualFile rootDir = project.getBaseDir();
      if (rootDir != null)
        candidates.addAll(findInDirectory(rootDir));
    }
    
    final VirtualFile path = getDefaultLocation();
    if (path != null)
      candidates.addAll(findInDirectory(path));

    for (VirtualFile file : getCondaDefaultLocations()) {
      candidates.addAll(findInDirectory(file));
    }

    final VirtualFile pyEnvLocation = getPyEnvDefaultLocations();
    if (pyEnvLocation != null) {
      candidates.addAll(findInDirectory(pyEnvLocation));
    }
    return candidates;
  }

  @Nullable
  public static VirtualFile getPyEnvDefaultLocations() {
    final String path = System.getenv().get("PYENV_ROOT");
    if (!StringUtil.isEmpty(path)) {
      final VirtualFile pyEnvRoot = LocalFileSystem.getInstance().findFileByPath(FileUtil.expandUserHome(path).replace('\\', '/'));
      if (pyEnvRoot != null) {
        return pyEnvRoot.findFileByRelativePath("versions");
      }
    }
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\','/'));
    if (userHome != null) {
      return userHome.findFileByRelativePath(".pyenv/versions");
    }
    return null;
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

  public static VirtualFile getDefaultLocation() {
    final String path = System.getenv().get("WORKON_HOME");
    if (!StringUtil.isEmpty(path)) {
      return LocalFileSystem.getInstance().findFileByPath(FileUtil.expandUserHome(path).replace('\\','/'));
    }

    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\','/'));
    if (userHome != null) {
      final VirtualFile predefinedFolder = userHome.findChild(".virtualenvs");
      if (predefinedFolder == null)
        return userHome;
      return predefinedFolder;
    }
    return null;
  }

  public static Collection<String> findInDirectory(VirtualFile rootDir) {
    List<String> candidates = new ArrayList<>();
    if (rootDir != null) {
      rootDir.refresh(true, false);
      VirtualFile[] suspects = rootDir.getChildren();
      for (VirtualFile child : suspects) {
        if (child.isDirectory()) {
          final VirtualFile bin = child.findChild("bin");
          final VirtualFile scripts = child.findChild("Scripts");
          if (bin != null) {
            final String interpreter = findInterpreter(bin);
            if (interpreter != null) candidates.add(interpreter);
          }
          if (scripts != null) {
            final String interpreter = findInterpreter(scripts);
            if (interpreter != null) candidates.add(interpreter);
          }
        }
      }
    }
    return candidates;
  }

  @Nullable
  private static String findInterpreter(VirtualFile dir) {
    for (VirtualFile child : dir.getChildren()) {
      if (!child.isDirectory()) {
        final String childName = child.getName().toLowerCase();
        for (String name : NAMES) {
          if (SystemInfo.isWindows) {
            if (childName.equals(name)) {
              return FileUtil.toSystemDependentName(child.getPath());
            }
          }
          else {
            if (childName.startsWith(name) || PYTHON_RE.matcher(childName).matches()) {
              if (!childName.endsWith("-config")) {
                return child.getPath();
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public boolean isValidSdkPath(@NotNull File file) {
    if (!super.isValidSdkPath(file)) return false;
    return PythonSdkType.getVirtualEnvRoot(file.getPath()) != null;
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Virtualenv;
  }
}
