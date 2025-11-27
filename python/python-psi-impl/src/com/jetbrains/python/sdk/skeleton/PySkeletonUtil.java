package com.jetbrains.python.sdk.skeleton;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

import static com.jetbrains.python.sdk.PySdkUtil.getLanguageLevelForSdk;
import static com.jetbrains.python.sdk.legacy.PythonSdkUtil.*;

/**
 * Skeleton logic from the original [com.jetbrains.python.sdk.PythonSdkUtil]
 */
@ApiStatus.Internal
public final class PySkeletonUtil {
  private static final Key<PySkeletonHeader> CACHED_SKELETON_HEADER = Key.create("CACHED_SKELETON_HEADER");

  private static @Nullable PySkeletonHeader readSkeletonHeader(@NotNull VirtualFile file, @NotNull Sdk pythonSdk) {
    final VirtualFile skeletonsDir = findSkeletonsDir(pythonSdk);
    if (skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, file, false)) {
      PySkeletonHeader skeletonHeader = file.getUserData(CACHED_SKELETON_HEADER);
      if (skeletonHeader == null) {
        skeletonHeader = PySkeletonHeader.readSkeletonHeader(VfsUtilCore.virtualToIoFile(file));
        file.putUserData(CACHED_SKELETON_HEADER, skeletonHeader);
      }
      return skeletonHeader;
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
  public static @Nullable VirtualFile getSitePackagesDirectory(@NotNull Sdk pythonSdk) {
    final VirtualFile libDir;
    if (isVirtualEnv(pythonSdk)) {
      libDir = PySearchUtilBase.findVirtualEnvLibDir(pythonSdk);
    }
    else {
      libDir = PySearchUtilBase.findLibDir(pythonSdk);
    }
    return libDir != null ? libDir.findChild(PyNames.SITE_PACKAGES) : null;
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
      if (PyTypeShed.INSTANCE.isInStandardLibrary(vFile)) {
        return true;
      }
    }
    return false;
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

  private static @NotNull String mapToRemote(@NotNull String localRoot, @NotNull Sdk sdk) {
    return PythonRuntimeService.getInstance().mapToRemote(localRoot, sdk);
  }

  /**
   * @return name of builtins skeleton file; for Python 2.x it is '{@code __builtins__.py}'.
   */


  @ApiStatus.Internal
  public static @NotNull @NonNls String getBuiltinsFileName(@NotNull Sdk sdk) {
    return PyBuiltinCache.getBuiltinsFileName(getLanguageLevelForSdk(sdk));
  }
}

