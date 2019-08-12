// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.ArrayUtil;
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
public class WinPythonSdkFlavor extends CPythonSdkFlavor {
  @NotNull
  private static final Key<String> APPX_PYTHON_CACHE = new Key<>("PythonFromStoreCache");
  private static final String NOTHING = "";
  private static final String[] REG_ROOTS = {"HKEY_LOCAL_MACHINE", "HKEY_CURRENT_USER"};
  private static final Map<String, String> REGISTRY_MAP =
    ImmutableMap.of("Python", "python.exe",
                    "IronPython", "ipy.exe");

  private static volatile Set<String> ourRegistryCache;

  @NotNull
  @Override
  public Collection<String> suggestHomePaths(@Nullable final Module module, @Nullable final UserDataHolder context) {
    Set<String> candidates = new TreeSet<>();
    findInCandidatePaths(candidates, "python.exe", "jython.bat", "pypy.exe");
    findInstallations(candidates, "python.exe", PythonHelpersLocator.getHelpersRoot().getParent());

    if (SystemInfo.isWin10OrNewer) {
      // For pythons installed from WindowsStore
      final VirtualFile installLocation = getInstallationLocationForStoreWithCache(context);
      if (installLocation != null) {
        final VirtualFile pythonFromStore = installLocation.findChild("python.exe");
        if (pythonFromStore != null) {
          candidates.add(pythonFromStore.getPath());
        }
      }
    }

    return candidates;
  }

  @Nullable
  private static VirtualFile getInstallationLocationForStoreWithCache(@Nullable final UserDataHolder context) {
    final VirtualFileSystem fs = LocalFileSystem.getInstance();

    if (context != null) {
      synchronized (APPX_PYTHON_CACHE) {
        final String result = context.getUserData(APPX_PYTHON_CACHE);
        if (result != null) {
          return result.equals(NOTHING) ? null : fs.refreshAndFindFileByPath(result);
        }
        final VirtualFile python = getInstallationLocationForStore(fs);
        context.putUserData(APPX_PYTHON_CACHE, python != null ? python.getPath() : NOTHING);
        return python;
      }
    }
    return getInstallationLocationForStore(fs);
  }

  @Nullable
  private static VirtualFile getInstallationLocationForStore(@NotNull final VirtualFileSystem fs) {
    return WindowsStoreServiceKt.findInstallLocationForPackage("Python", fs);
  }

  private void findInCandidatePaths(Set<String> candidates, String... exe_names) {
    for (String name : exe_names) {
      findInstallations(candidates, name, "C:\\", "C:\\Program Files\\");
      findInPath(candidates, name);


      findInRegistry(candidates);
    }
  }

  @Override
  public boolean isValidSdkHome(@NotNull final String path) {
    if (super.isValidSdkHome(path)) {
      return true;
    }

    final File file = new File(path);
    return mayBeAppXReparsePoint(file) && isValidSdkPath(file);
  }

  /**
   * AppX packages installed to AppX volume (see <code>Get-AppxDefaultVolume</code>).
   * At the same time, <strong>reparse point</strong> is created somewhere in <code>%LOCALAPPDATA%</code>.
   * This point has tag <code>IO_REPARSE_TAG_APPEXECLINK</code> and it also added to <code>PATH</code>
   * <br/>
   * Such points can't be read. Their attributes are also inaccessible. {@link File#exists()} returns false.
   * But when executed, they are processed by NTFS filter and redirected to their real location in AppX volume.
   * They are also returned with parent's {@link File#listFiles()}
   * <br/>
   * There is no Java API to fetch reparse data, and its structure is undocumented (although pretty simple), so we workaround it
   */
  private static boolean mayBeAppXReparsePoint(@NotNull final File file) {
    if (!SystemInfo.isWin10OrNewer) {
      return false;
    }

    final String localAppData = System.getenv("LOCALAPPDATA");
    if (localAppData == null) {
      return false;
    }
    final File localAppDataFile = new File(localAppData);

    if (!FileUtil.isAncestor(localAppDataFile, file, true)) {
      return false;
    }
    final File parent = file.getParentFile();
    if (parent == null) {
      return false;
    }
    final File[] files = parent.listFiles();
    return (files != null && ArrayUtil.contains(file, files));
  }


  void findInRegistry(@NotNull final Collection<String> candidates) {
    fillRegistryCache(getWinRegistryService());
    candidates.addAll(ourRegistryCache);
  }

  @NotNull
  protected WinRegistryService getWinRegistryService() {
    return ApplicationManager.getApplication().getService(WinRegistryService.class);
  }

  private static void findInstallations(Set<String> candidates, String exe_name, String... roots) {
    for (String root : roots) {
      findSubdirInstallations(candidates, root, FileUtilRt.getNameWithoutExtension(exe_name), exe_name);
    }
  }

  public static void findInPath(Collection<? super String> candidates, String exeName) {
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
        if (dir.isDirectory() && StringUtil.toLowerCase(dir.getName()).startsWith(dir_prefix)) {
          VirtualFile python_exe = dir.findChild(exe_name);
          if (python_exe != null) candidates.add(FileUtil.toSystemDependentName(python_exe.getPath()));
        }
      }
    }
  }
}
