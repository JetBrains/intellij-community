package com.jetbrains.python.sdk;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.module.PyModuleService;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.sdk.skeleton.PySkeletonHeader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility methods for Python {@link Sdk} based on the project model and the file system.
 *
 * TODO: Extract SDK "flavor" specific methods into a "Python SDK provider" so that each SDK flavor can be defined independently
 *
 * @see PySdkUtil for run-time Python SDK utils
 */
public class PythonSdkUtil {

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
  private static final String[] DIRS_WITH_BINARY = {"", "bin", "Scripts", "net45"};
  private static final String[] UNIX_BINARY_NAMES = {"jython", "pypy", "python", "python3"};
  private static final String[] WIN_BINARY_NAMES = {"jython.bat", "ipy.exe", "pypy.exe", "python.exe", "python3.exe"};
  private static final Predicate<Sdk> REMOTE_SDK_PREDICATE = PythonSdkUtil::isRemote;

  public static boolean isPythonSdk(@NotNull Sdk sdk) {
    return PyNames.PYTHON_SDK_ID_NAME.equals(sdk.getSdkType().getName());
  }

  public static List<Sdk> getAllSdks() {
    return ContainerUtil.filter(ProjectJdkTable.getInstance().getAllJdks(), PythonSdkUtil::isPythonSdk);
  }

  @Nullable
  private static PySkeletonHeader readSkeletonHeader(@NotNull VirtualFile file, @NotNull Sdk pythonSdk) {
    final VirtualFile skeletonsDir = findSkeletonsDir(pythonSdk);
    if (skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, file, false)) {
      return PySkeletonHeader.readSkeletonHeader(VfsUtilCore.virtualToIoFile(file));
    }
    return null;
  }

  public static boolean isStdLib(@NotNull VirtualFile vFile, @Nullable Sdk pythonSdk) {
    if (pythonSdk != null) {
      @Nullable VirtualFile originFile = vFile;
      @NotNull String originPath = vFile.getPath();
      boolean checkOnRemoteFS = false;
      // All binary skeletons are collected under the same root regardless of their original location.
      // Because of that we need to use paths to the corresponding binary modules recorded in their headers.
      final PySkeletonHeader header = readSkeletonHeader(originFile, pythonSdk);
      if (header != null) {
        // Binary module paths in skeleton headers of Mock SDK don't map to actual physical files.
        // Fallback to the old heuristic for these stubs.
        if (ApplicationManager.getApplication().isUnitTestMode() &&
            Objects.equals(vFile.getParent(), findSkeletonsDir(pythonSdk))) {
          return true;
        }

        final String binaryPath = header.getBinaryFile();
        // XXX Assume that all pre-generated stubs belong to the interpreter's stdlib -- might change in future with PY-32229
        if (binaryPath.equals(PySkeletonHeader.BUILTIN_NAME) || binaryPath.equals(PySkeletonHeader.PREGENERATED)) {
          return true;
        }
        if (isRemote(pythonSdk)) {
          checkOnRemoteFS = true;
          // Actual file is on remote file system and not available
          originFile = null;
        }
        else {
          originFile = VfsUtil.findFileByIoFile(new File(binaryPath), true);
        }
        originPath = binaryPath;
      }
      if (originFile != null) {
        originFile = ObjectUtils.notNull(originFile.getCanonicalFile(), originFile);
        originPath = originFile.getPath();
      }

      final VirtualFile libDir = PySearchUtilBase.findLibDir(pythonSdk);
      if (libDir != null && isUnderLibDirButNotSitePackages(originFile, originPath, libDir, pythonSdk, checkOnRemoteFS)) {
        return true;
      }
      final VirtualFile venvLibDir = PySearchUtilBase.findVirtualEnvLibDir(pythonSdk);
      if (venvLibDir != null && isUnderLibDirButNotSitePackages(originFile, originPath, venvLibDir, pythonSdk, checkOnRemoteFS)) {
        return true;
      }
      if (PyUserSkeletonsUtil.isStandardLibrarySkeleton(vFile)) {
        return true;
      }
      if (PyTypeShed.INSTANCE.isInStandardLibrary(vFile) && PyTypeShed.INSTANCE.isInside(vFile)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static Sdk findPythonSdk(@Nullable Module module) {
    if (module == null) return null;
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null && isPythonSdk(sdk)) return sdk;
    return PyModuleService.getInstance().findPythonSdk(module);
  }


  public static boolean isRemote(@Nullable String sdkPath) {
    return isRemote(findSdkByPath(sdkPath));
  }

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

  public static boolean isFileInSkeletons(@NotNull final VirtualFile virtualFile, @NotNull Sdk sdk) {
    final VirtualFile skeletonsDir = findSkeletonsDir(sdk);
    return skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, virtualFile, false);
  }

  public static boolean isElementInSkeletons(@NotNull final PsiElement element) {
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
  @NotNull
  public static String getSkeletonsPath(String basePath, String sdkHome) {
    String sep = File.separator;
    return getSkeletonsRootPath(basePath) + sep + FileUtil.toSystemIndependentName(sdkHome).hashCode() + sep;
  }

  @Nullable
  public static String getSkeletonsPath(@NotNull Sdk sdk) {
    String path = sdk.getHomePath();
    return path != null ? getSkeletonsPath(PathManager.getSystemPath(), path) : null;
  }

  @NotNull
  public static String getSkeletonsRootPath(String basePath) {
    return basePath + File.separator + SKELETON_DIR_NAME;
  }

  public static String getRemoteSourcesLocalPath(String sdkHome) {
    String sep = File.separator;

    String basePath = PathManager.getSystemPath();
    return basePath +
           File.separator +
           REMOTE_SOURCES_DIR_NAME +
           sep +
           FileUtil.toSystemIndependentName(sdkHome).hashCode() +
           sep;
  }

  @Nullable
  public static VirtualFile findSkeletonsDir(@NotNull final Sdk sdk) {
    return findLibraryDir(sdk, SKELETON_DIR_NAME, BUILTIN_ROOT_TYPE);
  }

  @Nullable
  public static VirtualFile findAnyRemoteLibrary(@NotNull final Sdk sdk) {
    return findLibraryDir(sdk, REMOTE_SOURCES_DIR_NAME, OrderRootType.CLASSES);
  }

  private static VirtualFile findLibraryDir(Sdk sdk, String dirName, OrderRootType rootType) {
    final VirtualFile[] virtualFiles = sdk.getRootProvider().getFiles(rootType);
    for (VirtualFile virtualFile : virtualFiles) {
      if (virtualFile.isValid() && virtualFile.getPath().contains(dirName)) {
        return virtualFile;
      }
    }
    return null;
  }

  @NotNull
  private static String mapToRemote(@NotNull String localRoot, @NotNull Sdk sdk) {
    return PythonRuntimeService.getInstance().mapToRemote(localRoot, sdk);
  }

  private static boolean isUnderLibDirButNotSitePackages(@Nullable VirtualFile file,
                                                         @NotNull String path,
                                                         @NotNull VirtualFile libDir,
                                                         @NotNull Sdk sdk,
                                                         boolean checkOnRemoteFS) {
    final VirtualFile originLibDir;
    final String originLibDirPath;
    if (checkOnRemoteFS) {
      originLibDir = libDir;
      originLibDirPath = mapToRemote(originLibDir.getPath(), sdk);
    }
    else {
      // Normalize the path to the lib directory on local FS
      originLibDir = ObjectUtils.notNull(libDir.getCanonicalFile(), libDir);
      originLibDirPath = originLibDir.getPath();
    }

    // This check is more brittle and thus used as a fallback measure
    if (checkOnRemoteFS || file == null) {
      final String normalizedLidDirPath = FileUtil.toSystemIndependentName(originLibDirPath);
      final String sitePackagesPath = normalizedLidDirPath + "/" + PyNames.SITE_PACKAGES;
      final String normalizedPath = FileUtil.toSystemIndependentName(path);
      return FileUtil.startsWith(normalizedPath, normalizedLidDirPath) && !FileUtil.startsWith(normalizedPath, sitePackagesPath);
    }
    else if (VfsUtilCore.isAncestor(originLibDir, file, false)) {
      final VirtualFile sitePackagesDir = originLibDir.findChild(PyNames.SITE_PACKAGES);
      return sitePackagesDir == null || !VfsUtilCore.isAncestor(sitePackagesDir, file, false);
    }
    return false;
  }

  public static boolean hasValidSdk() {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (isPythonSdk(sdk)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isInvalid(@NotNull Sdk sdk) {
    if (isRemote(sdk)) {
      return PyRemoteSdkValidator.Companion.isInvalid(sdk);
    }
    final VirtualFile interpreter = sdk.getHomeDirectory();
    return interpreter == null || !interpreter.exists();
  }

  public static boolean isDisposed(@NotNull Sdk sdk) {
    return sdk instanceof Disposable && Disposer.isDisposed((Disposable)sdk);
  }

  public static List<Sdk> getAllLocalCPythons() {
    return getAllSdks().stream().filter(REMOTE_SDK_PREDICATE.negate()).collect(Collectors.toList());
  }

  @Nullable
  public static String getPythonExecutable(@NotNull String rootPath) {
    final File rootFile = new File(rootPath);
    if (rootFile.isFile()) {
      return rootFile.getAbsolutePath();
    }
    for (String dir : DIRS_WITH_BINARY) {
      final File subDir;
      if (StringUtil.isEmpty(dir)) {
        subDir = rootFile;
      }
      else {
        subDir = new File(rootFile, dir);
      }
      if (!subDir.isDirectory()) {
        continue;
      }
      for (String binaryName : getBinaryNames()) {
        final File executable = new File(subDir, binaryName);
        if (executable.isFile()) {
          return executable.getAbsolutePath();
        }
      }
    }
    return null;
  }

  @Nullable
  public static String getExecutablePath(@NotNull final String homeDirectory, @NotNull String name) {
    File binPath = new File(homeDirectory);
    File binDir = binPath.getParentFile();
    if (binDir == null) return null;
    VirtualFileSystem localVfs = StandardFileSystems.local();
    File runner = new File(binDir, name);
    if (runner.exists()) return localVfs.extractPresentableUrl(runner.getPath());
    runner = new File(new File(binDir, "Scripts"), name);
    if (runner.exists()) return localVfs.extractPresentableUrl(runner.getPath());
    runner = new File(new File(binDir.getParentFile(), "Scripts"), name);
    if (runner.exists()) return localVfs.extractPresentableUrl(runner.getPath());
    runner = new File(new File(binDir.getParentFile(), "local"), name);
    if (runner.exists()) return localVfs.extractPresentableUrl(runner.getPath());
    runner = new File(new File(new File(binDir.getParentFile(), "local"), "bin"), name);
    if (runner.exists()) return localVfs.extractPresentableUrl(runner.getPath());

    // if interpreter is a symlink
    if (FileSystemUtil.isSymLink(homeDirectory)) {
      String resolvedPath = FileSystemUtil.resolveSymLink(homeDirectory);
      if (resolvedPath != null) {
        return getExecutablePath(resolvedPath, name);
      }
    }
    // Search in standard unix path
    runner = new File(new File("/usr", "bin"), name);
    if (runner.exists()) return localVfs.extractPresentableUrl(runner.getPath());
    runner = new File(new File(new File("/usr", "local"), "bin"), name);
    if (runner.exists()) return localVfs.extractPresentableUrl(runner.getPath());
    return null;
  }

  /**
   * @param binaryPath must point to a Python interpreter
   * @return if the surroundings look like a virtualenv installation, its root is returned (normally the grandparent of binaryPath).
   */
  @Nullable
  public static File getVirtualEnvRoot(@NotNull final String binaryPath) {
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
  @Nullable
  public static File findExecutableFile(File parent, String name) {
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

  private static String[] getBinaryNames() {
    if (SystemInfo.isUnix) {
      return UNIX_BINARY_NAMES;
    }
    else {
      return WIN_BINARY_NAMES;
    }
  }

  @Nullable
  public static Sdk findSdkByKey(@NotNull String key) {
    return ProjectJdkTable.getInstance().findJdk(key);
  }

  @Nullable
  public static Sdk findPythonSdk(@NotNull final PsiElement element) {
    return findPythonSdk(ModuleUtilCore.findModuleForPsiElement(element));
  }

  @Nullable
  public static Sdk findSdkByPath(@Nullable String path) {
    if (path != null) {
      return findSdkByPath(getAllSdks(), path);
    }
    return null;
  }

  @Nullable
  public static Sdk findSdkByPath(List<? extends Sdk> sdkList, @Nullable String path) {
    if (path != null) {
      for (Sdk sdk : sdkList) {
        if (sdk != null && FileUtil.pathsEqual(path, sdk.getHomePath())) {
          return sdk;
        }
      }
    }
    return null;
  }

  /**
   * Returns the "site-packages" directory that is going to be used for installing new packages with {@code pip}.
   * <p>
   * Note that on a virtual env there might be two such directories in {@code sys.path} depending on whether
   * the option "--system-site-packages" was given during its creation. Then the one inside the actual virtual
   * env tree will be returned, as it's the one used to install new packages.
   * Also, on some systems, first of all in system distributions of Python on Linux, there might be no
   * "site-packages" at all, and this method returns {@code null} accordingly in this case.
   */
  @Nullable
  public static VirtualFile getSitePackagesDirectory(@NotNull Sdk pythonSdk) {
    final VirtualFile libDir;
    if (isVirtualEnv(pythonSdk)) {
      libDir = PySearchUtilBase.findVirtualEnvLibDir(pythonSdk);
    }
    else {
      libDir = PySearchUtilBase.findLibDir(pythonSdk);
    }
    return libDir != null ? libDir.findChild(PyNames.SITE_PACKAGES) : null;
  }

  public static boolean isVirtualEnv(@NotNull Sdk sdk) {
    final String path = sdk.getHomePath();
    return isVirtualEnv(path);
  }

  @Contract("null -> false")
  public static boolean isVirtualEnv(@Nullable String path) {
    return path != null && getVirtualEnvRoot(path) != null;
  }

  @Nullable
  public static VirtualFile getCondaDirectory(@NotNull Sdk sdk) {
    final VirtualFile homeDirectory = sdk.getHomeDirectory();
    if (homeDirectory == null) return null;
    if (SystemInfo.isWindows) return homeDirectory.getParent();
    return homeDirectory.getParent().getParent();
  }

  public static boolean isCondaVirtualEnv(@NotNull Sdk sdk) {
    return isCondaVirtualEnv(sdk.getHomePath());
  }

  public static boolean isCondaVirtualEnv(@Nullable String sdkPath) {
    final VirtualFile condaMeta = findCondaMeta(sdkPath);
    if (condaMeta == null) {
      return false;
    }
    final VirtualFile envs = condaMeta.getParent().findChild("envs");
    return envs == null;
  }

  // Conda virtual environment and base conda
  public static boolean isConda(@NotNull Sdk sdk) {
    return isConda(sdk.getHomePath());
  }

  public static boolean isConda(@Nullable String sdkPath) {
    return findCondaMeta(sdkPath) != null;
  }

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

  @Nullable
  private static VirtualFile findCondaMeta(@Nullable String sdkPath) {
    if (sdkPath == null) {
      return null;
    }
    final VirtualFile homeDirectory = StandardFileSystems.local().findFileByPath(sdkPath);
    if (homeDirectory == null) {
      return null;
    }
    final VirtualFile condaParent = SystemInfo.isWindows ? homeDirectory.getParent()
                                                         : homeDirectory.getParent().getParent();
    return condaParent.findChild("conda-meta");
  }
}
