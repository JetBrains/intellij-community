package com.jetbrains.python.sdk.legacy;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.module.PyModuleService;
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvironment;
import com.jetbrains.python.sdk.PythonEnvironmentKt;
import com.jetbrains.python.venvReader.VirtualEnvReaderKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility methods for Python {@link Sdk} based on the project model and the file system.
 * <p>
 * TODO: Extract SDK "flavor" specific methods into a "Python SDK provider" so that each SDK flavor can be defined independently
 *
 * @see PySdkUtil for run-time Python SDK utils
 */
@ApiStatus.Internal
public final class PythonSdkUtil {

  public static final String REMOTE_SOURCES_DIR_NAME = "remote_sources";

  /**
   * Name of directory where skeleton files (despite the value) are stored.
   */
  public static final String SKELETON_DIR_NAME = "python_stubs";
  /**
   * In which root type built-in skeletons are put.
   */
  public static final OrderRootType BUILTIN_ROOT_TYPE = OrderRootType.CLASSES;
  private static final Predicate<Sdk> REMOTE_SDK_PREDICATE = PythonSdkUtil::isRemote;

  public static boolean isPythonSdk(@NotNull Sdk sdk) {
    return isPythonSdk(sdk, false);
  }

  public static boolean isPythonSdk(@NotNull Sdk sdk, boolean allowRemoteInFreeTier) {
    if (!PyNames.PYTHON_SDK_ID_NAME.equals(sdk.getSdkType().getName())) {
      return false;
    }

    // PY-79923: Should explicitly filter sdks created while pro was active
    if (isFreeTier()) {
      return allowRemoteInFreeTier || (!isRemote(sdk));
    }

    return true;
  }

  /**
   * @return PyCharm with Pro mode disabled
   */
  public static boolean isFreeTier() {
    return PlatformUtils.isPyCharm() &&
           PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID);
  }

  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks() {
    return getAllSdks(false);
  }

  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks(boolean allowRemoteInFreeTier) {
    return ContainerUtil.filter(ProjectJdkTable.getInstance().getAllJdks(), sdk -> isPythonSdk(sdk, allowRemoteInFreeTier));
  }

  /**
   * Consider to use suspended {@link com.jetbrains.python.sdk.ModuleExKt#findPythonSdk} instead, it waits for project model to be ready
   */
  @ApiStatus.Obsolete
  public static @Nullable Sdk findPythonSdk(@Nullable Module module) {
    if (module == null || module.isDisposed()) {
      return null;
    }
    var sdk = PyModuleService.getInstance(module.getProject()).findPythonSdk(module);
    if (sdk != null && isPythonSdk(sdk)) {
      return sdk;
    }

    return null;
  }

  /**
   * Checks if SDK is legacy remote or remote bases on targets.
   * Never assume {@link Sdk#getSdkAdditionalData()} has certain type if this method returns true.
   * In most cases you are encouraged to obtain additional data and check it explicitly
   */
  public static boolean isRemote(@Nullable Sdk sdk) {
    return sdk != null && sdk.getSdkAdditionalData() instanceof PyRemoteSdkAdditionalDataMarker;
  }

  public static @NlsSafe String getUserSite() {
    if (SystemInfo.isWindows) {
      final String appdata = System.getenv("APPDATA");
      return appdata + File.separator + "Python";
    }
    else {
      final String userHome = SystemProperties.getUserHome();
      return userHome + File.separator + ".local";
    }
  }

  public static boolean isFileInSkeletons(final @NotNull VirtualFile virtualFile, @NotNull Sdk sdk) {
    final VirtualFile skeletonsDir = findSkeletonsDir(sdk);
    return skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, virtualFile, false);
  }

  public static boolean isElementInSkeletons(final @NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file != null) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final Sdk sdk = findPythonSdk(element);
        if (sdk != null && isFileInSkeletons(virtualFile, sdk)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns skeletons location on the local machine. Independent of SDK credentials type (e.g. ssh, Vagrant, Docker or else).
   */
  public static @NotNull String getSkeletonsPath(String basePath, String sdkHome) {
    String sep = File.separator;
    return getSkeletonsRootPath(basePath) + sep + FileUtil.toSystemIndependentName(sdkHome).hashCode() + sep;
  }

  public static @Nullable String getSkeletonsPath(@NotNull Sdk sdk) {
    String path = sdk.getHomePath();
    return path != null ? getSkeletonsPath(PathManager.getSystemPath(), path) : null;
  }

  public static @NotNull String getSkeletonsRootPath(String basePath) {
    return basePath + File.separator + SKELETON_DIR_NAME;
  }

  public static @Nullable VirtualFile findSkeletonsDir(final @NotNull Sdk sdk) {
    return findLibraryDir(sdk, SKELETON_DIR_NAME, BUILTIN_ROOT_TYPE);
  }

  public static @Nullable VirtualFile findAnyRemoteLibrary(final @NotNull Sdk sdk) {
    return findLibraryDir(sdk, REMOTE_SOURCES_DIR_NAME, OrderRootType.CLASSES);
  }

  public static VirtualFile findLibraryDir(Sdk sdk, String dirName, OrderRootType rootType) {
    final VirtualFile[] virtualFiles = sdk.getRootProvider().getFiles(rootType);
    for (VirtualFile virtualFile : virtualFiles) {
      if (virtualFile.isValid() && virtualFile.getPath().contains(dirName)) {
        return virtualFile;
      }
    }
    return null;
  }

  public static boolean hasValidSdk() {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (isPythonSdk(sdk)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isDisposed(@NotNull Sdk sdk) {
    return sdk instanceof Disposable && Disposer.isDisposed((Disposable)sdk);
  }

  public static List<Sdk> getAllLocalCPythons() {
    return getAllSdks().stream().filter(REMOTE_SDK_PREDICATE.negate()).collect(Collectors.toList());
  }

  // It is only here for external plugins
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable String getPythonExecutable(@NotNull String rootPath) {
    var python = VirtualEnvReaderKt.VirtualEnvReader().findPythonInPythonRoot(Path.of(rootPath));
    return (python != null) ? python.toString() : null;
  }

  /**
   * @deprecated use {@link #getExecutablePath(Path, String)}
   */
  @Deprecated
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable String getExecutablePath(final @NotNull String homeDirectory, @NotNull String name) {
    Path path = getExecutablePath(Path.of(homeDirectory), name);
    return (path != null) ? path.toString() : null;
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable Path getExecutablePath(@NotNull Path homeDirectory, @NotNull String name) {
    Path binDir = homeDirectory.getParent();
    if (binDir == null) return null;
    Path runner = binDir.resolve(name);
    if (Files.exists(runner)) return runner;
    runner = binDir.resolve("Scripts").resolve(name);
    if (Files.exists(runner)) return runner;

    if (binDir.getParent() != null) {
      runner = binDir.getParent().resolve("Scripts").resolve(name);
      if (Files.exists(runner)) return runner;
      runner = binDir.getParent().resolve("local").resolve(name);
      if (Files.exists(runner)) return runner;
      runner = binDir.getParent().resolve("local").resolve("bin").resolve(name);
      if (Files.exists(runner)) return runner;
    }

    // if interpreter is a symlink
    String homeDirectoryAbsolutePath = homeDirectory.toAbsolutePath().toString();
    if (FileSystemUtil.isSymLink(homeDirectoryAbsolutePath)) {
      String resolvedPath = FileSystemUtil.resolveSymLink(homeDirectoryAbsolutePath);
      if (resolvedPath != null && !resolvedPath.equals(homeDirectoryAbsolutePath)) {
        return getExecutablePath(Path.of(resolvedPath), name);
      }
    }
    // Search in standard unix path
    runner = Path.of("/usr", "bin", name);
    if (Files.exists(runner)) return runner;
    runner = Path.of("/usr", "local", "bin", name);
    if (Files.exists(runner)) return runner;
    return null;
  }

  public static @Nullable Sdk findSdkByKey(@NotNull String key) {
    return ProjectJdkTable.getInstance().findJdk(key);
  }

  public static @Nullable Sdk findPythonSdk(final @NotNull PsiElement element) {
    return findPythonSdk(ModuleUtilCore.findModuleForPsiElement(element));
  }

  /**
   * @deprecated path is not unique, use {@link #findSdkByKey(String)} instead
   */
  @Deprecated
  public static @Nullable Sdk findSdkByPath(@Nullable String path) {
    if (path != null) {
      return findSdkByPath(getAllSdks(), path);
    }
    return null;
  }


  /**
   * @deprecated path is not unique, use {@link #findSdkByKey(String)} instead
   */
  @Deprecated
  public static @Nullable Sdk findSdkByPath(List<? extends Sdk> sdkList, @Nullable String path) {
    if (path != null) {
      for (Sdk sdk : sdkList) {
        if (sdk != null && FileUtil.pathsEqual(path, sdk.getHomePath())) {
          return sdk;
        }
      }
    }
    return null;
  }

  @Deprecated(forRemoval = true)
  @Contract("null -> false")
  public static boolean isVirtualEnv(@Nullable String path) {
    if (path == null) return false;
    var envResult = PythonEnvironmentKt.detectPythonEnvironment(Path.of(path));
    var env = envResult.getSuccessOrNull();
    return env instanceof PythonEnvironment.Venv;
  }
}
