// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonHelpersLocator;
import kotlin.text.Regex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static com.jetbrains.python.sdk.PythonSdkUtilKtKt.tryResolvePath;
import static com.jetbrains.python.sdk.WinAppxToolsKt.getAppxFiles;
import static com.jetbrains.python.sdk.WinAppxToolsKt.getAppxProduct;

/**
 * This class knows how to find python in Windows Registry according to
 * <a href="https://www.python.org/dev/peps/pep-0514/">PEP 514</a>
 */
public class WinPythonSdkFlavor extends CPythonSdkFlavor<PyFlavorData.Empty> {
  private static final @NotNull String[] REG_ROOTS = {"HKEY_LOCAL_MACHINE", "HKEY_CURRENT_USER"};
  /**
   * There may be a lot of python files in APPX folder. We do not need "w" files, but may need "python[version]?.exe"
   */
  private static final Regex PYTHON_EXE = new Regex("^python[0-9.]*?.exe$");
  /**
   * All Pythons from WinStore have "Python[something]" as their product name
   */
  private static final String APPX_PRODUCT = "Python";
  private static final Map<String, String> REGISTRY_MAP =
    ImmutableMap.of("Python", "python.exe",
                    "IronPython", "ipy.exe");

  private final @NotNull SynchronizedClearableLazy<Set<String>> myRegistryCache =
    new SynchronizedClearableLazy<>(() -> findInRegistry(getWinRegistryService()));
  private final @NotNull SynchronizedClearableLazy<Set<String>> myAppxCache = new SynchronizedClearableLazy<>(
    WinPythonSdkFlavor::getPythonsFromStore);

  public static WinPythonSdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(WinPythonSdkFlavor.class);
  }

  @Override
  public boolean isApplicable() {
    return SystemInfo.isWindows;
  }

  @Override
  public @NotNull Class<PyFlavorData.Empty> getFlavorDataClass() {
    return PyFlavorData.Empty.class;
  }

  @Override
  public @NotNull Collection<@NotNull Path> suggestLocalHomePaths(final @Nullable Module module, final @Nullable UserDataHolder context) {
    Set<String> candidates = new TreeSet<>();
    findInCandidatePaths(candidates, "python.exe", "jython.bat", "pypy.exe");
    findInstallations(candidates, "python.exe", PythonHelpersLocator.getCommunityHelpersRoot().getParent().toString());
    return ContainerUtil.map(candidates, Path::of);
  }

  private void findInCandidatePaths(Set<String> candidates, String... exe_names) {
    for (String name : exe_names) {
      findInstallations(candidates, name, "C:\\", "C:\\Program Files\\");
      findInPath(candidates, name);
    }

    findInRegistry(candidates);
    candidates.addAll(myAppxCache.getValue());
  }

  @Override
  public final void resetHomePathCache() {
    myRegistryCache.drop();
  }

  @Override
  public boolean sdkSeemsValid(@NotNull Sdk sdk,
                               PyFlavorData.@NotNull Empty flavorData,
                               @Nullable TargetEnvironmentConfiguration targetConfig) {
    if (super.sdkSeemsValid(sdk, flavorData, targetConfig) || targetConfig != null) {
      // non-local, cant check for appx
      return true;
    }

    var path = tryResolvePath(sdk.getHomePath());
    return path != null && isLocalPathValidPython(path);
  }

  @Override
  public boolean isValidSdkPath(final @NotNull String pathStr) {
    if (super.isValidSdkPath(pathStr)) {
      return true;
    }

    var path = tryResolvePath(pathStr);
    return path != null && isLocalPathValidPython(path);
  }

  private boolean isLocalPathValidPython(@NotNull Path path) {
    String pathStr = path.toString();
    if (myAppxCache.getValue().contains(pathStr)) {
      return true;
    }

    String product = getAppxProduct(path);
    return product != null && product.contains(APPX_PRODUCT) && isValidSdkPath(pathStr);
  }

  @Override
  public void dropCaches() {
    myRegistryCache.drop();
    myAppxCache.drop();
  }


  void findInRegistry(final @NotNull Collection<String> candidates) {
    candidates.addAll(myRegistryCache.getValue());
  }

  protected @NotNull WinRegistryService getWinRegistryService() {
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

  private static @NotNull Set<String> getPythonsFromStore() {
    return ContainerUtil.map2Set(getAppxFiles(APPX_PRODUCT, PYTHON_EXE), file -> file.toAbsolutePath().toString());
  }

  private static @NotNull Set<String> findInRegistry(@NotNull WinRegistryService registryService) {
    final Set<String> result = new HashSet<>();

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
                  result.add(FileUtil.toSystemDependentName(interpreter.getPath()));
                }
              }
            }
          }
        }
      }
    }

    return result;
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
