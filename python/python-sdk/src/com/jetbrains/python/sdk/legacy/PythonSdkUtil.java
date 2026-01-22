package com.jetbrains.python.sdk.legacy;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.module.PyModuleService;
import com.jetbrains.python.sdk.CustomSdkHomePattern;
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.venvReader.VirtualEnvReader;
import com.jetbrains.python.venvReader.VirtualEnvReaderKt;
import org.jetbrains.annotations.*;

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
  static final String[] WINDOWS_EXECUTABLE_SUFFIXES = {"cmd", "exe", "bat", "com"};
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
    return PlatformUtils.isPyCharm() && (!PlatformUtils.isDataSpell()) && PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID);
  }

  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks() {
    return getAllSdks(false);
  }

  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks(boolean allowRemoteInFreeTier) {
    return ContainerUtil.filter(ProjectJdkTable.getInstance().getAllJdks(), sdk -> isPythonSdk(sdk, allowRemoteInFreeTier));
  }

  public static @Nullable Sdk findPythonSdk(@Nullable Module module) {
    if (module == null || module.isDisposed()) {
      return null;
    }
    var sdk = PyModuleService.getInstance().findPythonSdk(module);
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

  /**
   * @param binaryPath must point to a Python interpreter
   * @return if the surroundings look like a virtualenv installation, its root is returned (normally the grandparent of binaryPath).
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable File getVirtualEnvRoot(final @NotNull String binaryPath) {
    SlowOperations.assertSlowOperationsAreAllowed();
    final File bin = new File(binaryPath).getParentFile();
    if (bin != null) {
      final String rootPath = bin.getParent();
      if (rootPath != null) {
        final File root = new File(rootPath);
        final File activateThis = new File(bin, "activate_this.py");
        // binaryPath should contain an 'activate' script, and root should have bin (with us) and include and libp
        if (activateThis.exists()) {
          final File activate = findExecutableFile(bin, "activate");
          if (activate != null) {
            return root;
          }
        }
        // Python 3.3 virtualenvs can be found as described in PEP 405
        if (new File(root, "pyvenv.cfg").exists()) {
          return root;
        }
      }
    }
    return null;
  }

  /**
   * Finds a file that looks executable: an .exe or .cmd under windows, plain file under *nix.
   *
   * @param parent directory to look at
   * @param name   name of the executable without suffix
   * @return File representing the executable, or null.
   */
  public static @Nullable File findExecutableFile(File parent, String name) {
    if (SystemInfo.isWindows) {
      for (String suffix : WINDOWS_EXECUTABLE_SUFFIXES) {
        File file = new File(parent, name + "." + suffix);
        if (file.exists()) return file;
      }
    }
    else if (SystemInfo.isUnix) {
      File file = new File(parent, name);
      if (file.exists()) return file;
    }
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

  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean isVirtualEnv(@NotNull Sdk sdk) {
    final String path = sdk.getHomePath();
    return isVirtualEnv(path);
  }

  @Contract("null -> false")
  public static boolean isVirtualEnv(@Nullable String path) {
    return path != null && getVirtualEnvRoot(path) != null;
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable VirtualFile getCondaDirectory(@NotNull Sdk sdk) {
    final VirtualFile homeDirectory = sdk.getHomeDirectory();
    if (homeDirectory == null) return null;
    if (SystemInfo.isWindows) return homeDirectory.getParent();
    return homeDirectory.getParent().getParent();
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean isCondaVirtualEnv(@NotNull Sdk sdk) {
    return isCondaVirtualEnv(sdk.getHomePath());
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean isCondaVirtualEnv(@Nullable String sdkPath) {
    SlowOperations.assertSlowOperationsAreAllowed();
    final VirtualFile condaMeta = findCondaMeta(sdkPath);
    if (condaMeta == null) {
      return false;
    }
    final VirtualFile envs = condaMeta.getParent().findChild("envs");
    return envs == null;
  }

  /**
   * @deprecated Check sdk flavour instead
   */
  @Deprecated
  // Conda virtual environment and base conda
  public static boolean isConda(@NotNull Sdk sdk) {
    return isConda(sdk.getHomePath());
  }

  /**
   * @deprecated flavour instead
   */
  @Deprecated
  public static boolean isConda(@Nullable String sdkPath) {
    return findCondaMeta(sdkPath) != null;
  }

  /**
   * @deprecated flavour instead
   */
  @Deprecated
  public static boolean isBaseConda(@Nullable String sdkPath) {
    final VirtualFile condaMeta = findCondaMeta(sdkPath);
    if (condaMeta == null) {
      return false;
    }
    final VirtualFile parent = condaMeta.getParent();
    if (parent == null) {
      return false;
    }
    final VirtualFile condaBin = parent.findChild("condabin");
    if (condaBin != null) {
      return true;
    }
    return parent.findChild("envs") != null;
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable VirtualFile findCondaMeta(@Nullable String sdkPath) {
    SlowOperations.assertSlowOperationsAreAllowed();
    if (sdkPath == null || CustomSdkHomePattern.isCustomPythonSdkHomePath(sdkPath)) {
      return null;
    }
    final VirtualFile homeDirectory = StandardFileSystems.local().findFileByPath(sdkPath);
    if (homeDirectory == null) {
      return null;
    }
    VirtualFile parentDirectory = homeDirectory.getParent();
    if (parentDirectory == null) {
      return null;
    }
    final VirtualFile condaParent = SystemInfo.isWindows ? parentDirectory
                                                         : parentDirectory.getParent();
    return condaParent != null ? condaParent.findChild("conda-meta") : null;
  }
}
