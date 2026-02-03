package com.jetbrains.python.sdk;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.jetbrains.python.sdk.skeleton.PySkeletonUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;

/**
 * Old API only for external usages, non-deprecated yet because there is no alternative API.
 */
@SuppressWarnings("unused")
public final class PythonSdkUtil {

  public static final String REMOTE_SOURCES_DIR_NAME = com.jetbrains.python.sdk.legacy.PythonSdkUtil.REMOTE_SOURCES_DIR_NAME;
  public static final String SKELETON_DIR_NAME = com.jetbrains.python.sdk.legacy.PythonSdkUtil.SKELETON_DIR_NAME;
  public static final OrderRootType BUILTIN_ROOT_TYPE = com.jetbrains.python.sdk.legacy.PythonSdkUtil.BUILTIN_ROOT_TYPE;

  public static boolean isPythonSdk(@NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isPythonSdk(sdk);
  }

  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks() {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getAllSdks();
  }

  public static boolean isStdLib(@NotNull VirtualFile vFile, @Nullable Sdk pythonSdk) {
    return PySkeletonUtil.isStdLib(vFile, pythonSdk);
  }

  public static @Nullable Sdk findPythonSdk(@Nullable Module module) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.findPythonSdk(module);
  }

  public static boolean isRemote(@Nullable Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote(sdk);
  }

  public static @NlsSafe String getUserSite() {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getUserSite();
  }

  public static @Nullable VirtualFile findSkeletonsDir(final @NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.findSkeletonsDir(sdk);
  }

  /**
   * @deprecated use PySdkExt.isSdkSeemsValid
   */
  @Deprecated(forRemoval = true)
  public static boolean isInvalid(@NotNull Sdk sdk) {
    if (isRemote(sdk)) {
      return true;
    }
    final VirtualFile interpreter = sdk.getHomeDirectory();
    return interpreter == null || !interpreter.exists();
  }

  public static boolean isDisposed(@NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isDisposed(sdk);
  }

  public static List<Sdk> getAllLocalCPythons() {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getAllLocalCPythons();
  }

  // It is only here for external plugins
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable String getPythonExecutable(@NotNull String rootPath) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getPythonExecutable(rootPath);
  }

  /**
   * @deprecated use {@link #getExecutablePath(Path, String)}
   */
  @Deprecated
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable String getExecutablePath(final @NotNull String homeDirectory, @NotNull String name) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getExecutablePath(homeDirectory, name);
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable Path getExecutablePath(@NotNull Path homeDirectory, @NotNull String name) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getExecutablePath(homeDirectory, name);
  }

  public static @Nullable Sdk findSdkByKey(@NotNull String key) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.findSdkByKey(key);
  }

  public static @Nullable Sdk findPythonSdk(final @NotNull PsiElement element) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.findPythonSdk(element);
  }

  /**
   * @deprecated path is not unique, use {@link #findSdkByKey(String)} instead
   */
  @Deprecated
  public static @Nullable Sdk findSdkByPath(@Nullable String path) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.findSdkByPath(path);
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
    return PySkeletonUtil.getSitePackagesDirectory(pythonSdk);
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean isVirtualEnv(@NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isVirtualEnv(sdk);
  }

  @Contract("null -> false")
  public static boolean isVirtualEnv(@Nullable String path) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isVirtualEnv(path);
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean isCondaVirtualEnv(@NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isCondaVirtualEnv(sdk);
  }

  /**
   * @deprecated Check sdk flavour instead
   */
  @Deprecated
  // Conda virtual environment and base conda
  public static boolean isConda(@NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isConda(sdk);
  }
}
