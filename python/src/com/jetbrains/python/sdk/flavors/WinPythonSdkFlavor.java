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

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.WindowsRegistryUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.jetbrains.python.PythonHelpersLocator;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class WinPythonSdkFlavor extends CPythonSdkFlavor {
  public static WinPythonSdkFlavor INSTANCE = new WinPythonSdkFlavor();
  private static Map<String, String> ourRegistryMap =
    ImmutableMap.of("HKEY_LOCAL_MACHINE\\SOFTWARE\\Python\\PythonCore", "python.exe",
                    "HKEY_LOCAL_MACHINE\\SOFTWARE\\Wow6432Node\\Python\\PythonCore", "python.exe",
                    "HKEY_LOCAL_MACHINE\\SOFTWARE\\IronPython", "ipy.exe");

  private static Set<String> ourRegistryCache;

  private WinPythonSdkFlavor() {
  }

  @Override
  public Collection<String> suggestHomePaths() {
    Set<String> candidates = new TreeSet<>();
    findInCandidatePaths(candidates, "python.exe", "jython.bat", "pypy.exe");
    findInstallations(candidates, "python.exe", PythonHelpersLocator.getHelpersRoot().getParent());
    return candidates;
  }

  private static void findInCandidatePaths(Set<String> candidates, String... exe_names) {
    for (String name : exe_names) {
      findInstallations(candidates, name, "C:\\", "C:\\Program Files\\");
      findInPath(candidates, name);
      findInRegistry(candidates);
    }
  }

  private static void findInstallations(Set<String> candidates, String exe_name, String... roots) {
    for (String root : roots) {
      findSubdirInstallations(candidates, root, FileUtil.getNameWithoutExtension(exe_name), exe_name);
    }
  }

  public static void findInPath(Collection<String> candidates, String exeName) {
    final String path = System.getenv("PATH");
    if (path == null) return;
    for (String pathEntry : StringUtil.split(path, ";")) {
      if (pathEntry.startsWith("\"") && pathEntry.endsWith("\"")) {
        if (pathEntry.length() < 2) continue;
        pathEntry = pathEntry.substring(1, pathEntry.length() - 1);
      }
      File f = new File(pathEntry, exeName);
      if (f.exists()) {
        candidates.add(FileUtil.toSystemDependentName(f.getPath()));
      }
    }
  }

  public static void findInRegistry(Collection<String> candidates) {
    fillRegistryCache();
    candidates.addAll(ourRegistryCache);
  }

  private static void fillRegistryCache() {
    if (ourRegistryCache == null) {
      ourRegistryCache = new HashSet<>();
      for (Map.Entry<String, String> entry : ourRegistryMap.entrySet()) {
        final String prefix = entry.getKey();
        final String exePath = entry.getValue();
        List<String> strings = WindowsRegistryUtil.readRegistryBranch(prefix);
        for (String string : strings) {
          final String path = WindowsRegistryUtil.readRegistryDefault(prefix + "\\" + string +
                                                                      "\\InstallPath");
          if (path != null) {
            File f = new File(path, exePath);
            if (f.exists()) {
              ourRegistryCache.add(FileUtil.toSystemDependentName(f.getPath()));
            }
          }
        }
      }
    }
  }

  private static void findSubdirInstallations(Collection<String> candidates, String rootDir, String dir_prefix, String exe_name) {
    VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(rootDir);
    if (rootVDir != null) {
      if (rootVDir instanceof NewVirtualFile) {
        ((NewVirtualFile)rootVDir).markDirty();
      }
      rootVDir.refresh(true, false);
      for (VirtualFile dir : rootVDir.getChildren()) {
        if (dir.isDirectory() && dir.getName().toLowerCase().startsWith(dir_prefix)) {
          VirtualFile python_exe = dir.findChild(exe_name);
          if (python_exe != null) candidates.add(FileUtil.toSystemDependentName(python_exe.getPath()));
        }
      }
    }
  }
}
