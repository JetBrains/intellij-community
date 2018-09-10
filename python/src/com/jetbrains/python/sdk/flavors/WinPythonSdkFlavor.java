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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * This class knows how to find python in Windows Registry according to
 * <a href="https://www.python.org/dev/peps/pep-0514/">PEP 514</a>
 *
 * @author yole
 */
public final class WinPythonSdkFlavor extends CPythonSdkFlavor {
  private static final String[] REG_ROOTS = {"HKEY_LOCAL_MACHINE", "HKEY_CURRENT_USER"};
  private static final Map<String, String> REGISTRY_MAP =
    ImmutableMap.of("Python", "python.exe",
                    "IronPython", "ipy.exe");

  private static volatile Set<String> ourRegistryCache;
  @NotNull
  private final WinRegistryService myWinRegService;

  WinPythonSdkFlavor(@NotNull final WinRegistryService winRegistryService) {
    myWinRegService = winRegistryService;
  }

  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module) {
    Set<String> candidates = new TreeSet<>();
    findInCandidatePaths(candidates, "python.exe", "jython.bat", "pypy.exe");
    findInstallations(candidates, "python.exe", PythonHelpersLocator.getHelpersRoot().getParent());
    return candidates;
  }

  private void findInCandidatePaths(Set<String> candidates, String... exe_names) {
    for (String name : exe_names) {
      findInstallations(candidates, name, "C:\\", "C:\\Program Files\\");
      findInPath(candidates, name);


      findInRegistry(candidates);
    }
  }

  void findInRegistry(@NotNull final Collection<String> candidates) {
    fillRegistryCache(myWinRegService);
    candidates.addAll(ourRegistryCache);
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

  private static void fillRegistryCache(@NotNull final WinRegistryService registryService) {
    if (ourRegistryCache != null) {
      return;
    }
    ourRegistryCache = new HashSet<>();

    /*
     Check https://www.python.org/dev/peps/pep-0514/ for windows registry layout to understand
     this method
     */
    for (final String regRoot : REG_ROOTS) {
      for (final Map.Entry<String, String> entry : REGISTRY_MAP.entrySet()) {
        final String productId = entry.getKey();
        final String exePath = entry.getValue();
        final String companiesPath = String.format("%s\\SOFTWARE\\%s", regRoot, productId);
        final String companiesPathWow = String.format("%s\\SOFTWARE\\Wow6432Node\\%s", regRoot, productId);

        for (final String path : new String[]{companiesPath, companiesPathWow}) {
          final List<String> companies = registryService.listBranches(path);
          for (final String company : companies) {
            final String pathToCompany = path + '\\' + company;
            final List<String> versions = registryService.listBranches(pathToCompany);
            for (final String version : versions) {
              final String folder = registryService.getDefaultKey(pathToCompany + '\\' + version + "\\InstallPath");
              if (folder != null) {
                final File interpreter = new File(folder, exePath);
                if (interpreter.exists()) {
                  ourRegistryCache.add(FileUtil.toSystemDependentName(interpreter.getPath()));
                }
              }
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
