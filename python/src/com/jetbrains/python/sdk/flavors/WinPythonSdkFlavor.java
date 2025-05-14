// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonHelpersLocator;
import kotlin.text.Regex;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;

import static com.jetbrains.python.sdk.WinAppxToolsKt.getAppxFiles;
import static com.jetbrains.python.sdk.WinAppxToolsKt.getAppxProduct;
import static com.jetbrains.python.venvReader.ResolveUtilKt.tryResolvePath;

/**
 * This class knows how to find python in Windows Registry according to
 * <a href="https://www.python.org/dev/peps/pep-0514/">PEP 514</a>
 */
@ApiStatus.Internal

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
  /**
   * When looking for pythons, we start from `c:\`, but might use this property if set
   *
   * @see #findInCandidatePaths(Set, String...)
   */
  @ApiStatus.Internal
  @TestOnly
  public static final String ROOT_TO_SEARCH_PYTHON_IN = "pycharm.root.to.search.python.in";

  /**
   * Inside @{link {@link #ROOT_TO_SEARCH_PYTHON_IN}} directory must begin with this property (python otherwise)
   *
   * @see #findInstallations(Set, String, String...)
   */
  @TestOnly
  public static final String DIR_WITH_PYTHON_NAME = "dir_with_python_name";

  private final @NotNull SynchronizedClearableLazy<Set<String>> myRegistryCache =
    new SynchronizedClearableLazy<>(() -> findInRegistry(getWinRegistryService()));
  private final @NotNull SynchronizedClearableLazy<Set<String>> myAppxCache = new SynchronizedClearableLazy<>(
    WinPythonSdkFlavor::getPythonsFromStore);

  public static WinPythonSdkFlavor getInstance() {
    return EP_NAME.findExtension(WinPythonSdkFlavor.class);
  }

  @Override
  public boolean isApplicable() {
    return SystemInfo.isWindows;
  }

  @Override
  public @NotNull Class<PyFlavorData.Empty> getFlavorDataClass() {
    return PyFlavorData.Empty.class;
  }

  @RequiresBackgroundThread
  @Override
  protected final @NotNull Collection<@NotNull Path> suggestLocalHomePathsImpl(final @Nullable Module module,
                                                                               final @Nullable UserDataHolder context) {
    Set<String> candidates = new TreeSet<>();
    findInCandidatePaths(candidates, "python.exe", "pypy.exe");
    findInstallations(candidates, "python.exe", PythonHelpersLocator.getCommunityHelpersRoot().getParent().toString());
    return ContainerUtil.map(candidates, Path::of);
  }

  @RequiresBackgroundThread
  private void findInCandidatePaths(Set<String> candidates, String... exe_names) {
    @SuppressWarnings("TestOnlyProblems")
    var root = System.getProperty(ROOT_TO_SEARCH_PYTHON_IN, "C:\\");
    for (String name : exe_names) {
      findInstallations(candidates, name, root, "C:\\Program Files\\");
      findInPath(candidates, name);
    }

    findInRegistry(candidates);
    candidates.addAll(myAppxCache.getValue());
  }

  @Override
  public final boolean sdkSeemsValid(@NotNull Sdk sdk,
                                     PyFlavorData.@NotNull Empty flavorData,
                                     @Nullable TargetEnvironmentConfiguration targetConfig) {
    if (super.sdkSeemsValid(sdk, flavorData, targetConfig) || targetConfig != null) {
      // non-local, cant check for appx
      return true;
    }

    String path = sdk.getHomePath();
    return path != null && isValidSdkPath(path);
  }

  @Override
  public final boolean isValidSdkPath(final @NotNull String pathStr) {
    if (super.isValidSdkPath(pathStr)) {
      return true; // File is local and executable
    }

    var path = tryResolvePath(pathStr);
    return path != null && isPythonFromStore(path); // Python from store might be non-executable, but still usable
  }

  @RequiresBackgroundThread(generateAssertion = false) // Still used by some code from EDT
  private boolean isPythonFromStore(@NotNull Path path) {
    String pathStr = path.toString();
    if (myAppxCache.getValue().contains(pathStr)) {
      return true;
    }

    String product = getAppxProduct(path);
    return product != null && product.contains(APPX_PRODUCT);
  }

  @Override
  public void dropCaches() {
    myRegistryCache.drop();
    myAppxCache.drop();
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public void findInRegistry(final @NotNull Collection<String> candidates) {
    candidates.addAll(myRegistryCache.getValue());
  }

  protected @NotNull WinRegistryService getWinRegistryService() {
    return ApplicationManager.getApplication().getService(WinRegistryService.class);
  }

  @RequiresBackgroundThread
  private static void findInstallations(Set<String> candidates, String exe_name, String... roots) {
    @SuppressWarnings("TestOnlyProblems")
    var prefix = System.getProperty(DIR_WITH_PYTHON_NAME, FileUtilRt.getNameWithoutExtension(exe_name));
    for (String root : roots) {
      findSubdirInstallations(candidates, root, prefix, exe_name);
    }
  }

  @RequiresBackgroundThread
  private static void findInPath(@NotNull Collection<? super @NotNull String> candidates, @NotNull String exeName) {
    final String path = PathEnvironmentVariableUtil.getPathVariableValue(); //can be Path or PATH
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

  private static @Unmodifiable @NotNull Set<String> getPythonsFromStore() {
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


  /**
   * Given <pre>rootDir/dirStartsWithPrefix/exeName</pre> adds `exeName` to candidates.
   *
   * @param candidates adds a result here
   * @param rootDir    searching for files here
   * @param dirPrefix  if a child of root begins with it
   * @param exeName    and child of root contains this exe file
   */
  @RequiresBackgroundThread
  private static void findSubdirInstallations(@NotNull Collection<String> candidates,
                                              @NotNull String rootDir,
                                              @NotNull String dirPrefix,
                                              @NotNull String exeName) {
    try {
      var rootDirNio = Path.of(rootDir);
      try (var f = Files.newDirectoryStream(rootDirNio)) {
        for (Path dir : f) {
          if (Files.isDirectory(dir) && StringUtil.toLowerCase(dir.getFileName().toString()).startsWith(dirPrefix)) {
            var pythonExe = dir.resolve(exeName);
            if (Files.isExecutable(pythonExe)) candidates.add(FileUtil.toSystemDependentName(pythonExe.toString()));
          }
        }
      }
    }
    catch (IOException | InvalidPathException ignored) {
    }
  }
}
