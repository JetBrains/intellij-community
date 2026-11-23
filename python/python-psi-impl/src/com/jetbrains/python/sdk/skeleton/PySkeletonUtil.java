// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.skeleton;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

import static com.jetbrains.python.sdk.PySdkUtil.getLanguageLevelForSdk;

/**
 * Skeleton logic from the original [com.jetbrains.python.sdk.PythonSdkUtil]
 */
@ApiStatus.Internal
public final class PySkeletonUtil {
  private static final Key<PySkeletonHeader> CACHED_SKELETON_HEADER = Key.create("CACHED_SKELETON_HEADER");

  private static @Nullable PySkeletonHeader readSkeletonHeader(@NotNull VirtualFile file, @NotNull PythonInterpreter interpreter) {
    final VirtualFile skeletonsDir = PythonInterpreterExtKt.skeletonsDirectory(interpreter);
    if (skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, file, false)) {
      PySkeletonHeader skeletonHeader = file.getUserData(CACHED_SKELETON_HEADER);
      if (skeletonHeader == null) {
        skeletonHeader = PySkeletonHeader.readSkeletonHeader(file);
        file.putUserData(CACHED_SKELETON_HEADER, skeletonHeader);
      }
      return skeletonHeader;
    }
    return null;
  }

  /**
   * Whether {@code vFile} belongs to the standard library of {@code interpreter}.
   * <p>
   * Code insight calls it under a read action, so it reads no file outside the VFS.
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean isStdLib(@NotNull VirtualFile vFile, @Nullable PythonInterpreter interpreter) {
    if (interpreter == null) return false;
    @Nullable VirtualFile originFile = vFile;
    @NotNull String originPath = vFile.getPath();
    boolean checkOnRemoteFS = false;
    // All binary skeletons are collected under the same root regardless of their original location.
    // Because of that we need to use paths to the corresponding binary modules recorded in their headers.
    final PySkeletonHeader header = readSkeletonHeader(originFile, interpreter);
    if (header != null) {
      // Binary module paths in skeleton headers of Mock SDK don't map to actual physical files.
      // Fallback to the old heuristic for these stubs.
      if (ApplicationManager.getApplication().isUnitTestMode() &&
          Objects.equals(vFile.getParent(), PythonInterpreterExtKt.skeletonsDirectory(interpreter))) {
        return true;
      }

      final String binaryPath = header.getBinaryFile();
      if (binaryPath.equals(PySkeletonHeader.BUILTIN_NAME)) {
        return true;
      }
      if (PythonInterpreterExtKt.isOnTarget(interpreter)) {
        checkOnRemoteFS = true;
        // Actual file is on remote file system and not available
        originFile = null;
      }
      else {
        originFile = VfsUtil.findFile(Path.of(binaryPath), false);
      }
      originPath = binaryPath;
    }
    if (originFile != null) {
      originFile = ObjectUtils.notNull(originFile.getCanonicalFile(), originFile);
      originPath = originFile.getPath();
    }

    final VirtualFile libDir = PythonInterpreterExtKt.stdlibLibDirectory(interpreter);
    if (libDir != null && isUnderLibDirButNotSitePackages(originFile, originPath, libDir, interpreter, checkOnRemoteFS)) {
      return true;
    }
    final VirtualFile venvLibDir = PythonInterpreterExtKt.venvLibDirectory(interpreter);
    if (venvLibDir != null && isUnderLibDirButNotSitePackages(originFile, originPath, venvLibDir, interpreter, checkOnRemoteFS)) {
      return true;
    }
    if (PyTypeShed.INSTANCE.isInStandardLibrary(vFile)) {
      return true;
    }
    return false;
  }

  private static boolean isUnderLibDirButNotSitePackages(@Nullable VirtualFile file,
                                                         @NotNull String path,
                                                         @NotNull VirtualFile libDir,
                                                         @NotNull PythonInterpreter interpreter,
                                                         boolean checkOnRemoteFS) {
    final VirtualFile originLibDir;
    final String originLibDirPath;
    if (checkOnRemoteFS) {
      originLibDir = libDir;
      originLibDirPath = mapToRemote(originLibDir.getPath(), interpreter);
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

  private static @NotNull String mapToRemote(@NotNull String localRoot, @NotNull PythonInterpreter interpreter) {
    final PathMappingSettings mappings = PythonInterpreterExtKt.getPathMappings(interpreter);
    return mappings != null ? mappings.convertToRemote(localRoot) : localRoot;
  }

  /**
   * @return name of builtins skeleton file; for Python 2.x it is '{@code __builtins__.py}'.
   */


  @ApiStatus.Internal
  public static @NotNull @NonNls String getBuiltinsFileName(@NotNull Sdk sdk) {
    return PyBuiltinCache.getBuiltinsFileName(getLanguageLevelForSdk(sdk));
  }
}
