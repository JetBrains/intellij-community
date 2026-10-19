// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.sdk.backend.PythonEnvironment;
import com.intellij.python.sdk.backend.PythonEnvironmentExtKt;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreterKt;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.jetbrains.python.project.PyProject;
import com.jetbrains.python.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.sdk.skeleton.PySkeletonUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Old API only for external usages, non-deprecated yet because there is no alternative API.
 */
public final class PythonSdkUtil {
  private PythonSdkUtil() {
  }

  /** The `id` the venv provider declares in `intellij.python.venv.xml`. */
  private static final String VENV_KIND_ID = "venv";
  /** The `id` the conda provider declares in `intellij.python.community.impl.conda.xml`. */
  private static final String CONDA_KIND_ID = "conda";

  public static boolean isPythonSdk(@NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isPythonSdk(sdk);
  }

  /**
   * @deprecated Use {@link #getAllSdks(Project)} or {@link #getAllSdks(PyProject)}
   */
  @Deprecated(forRemoval = true)
  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks() {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getAllSdks();
  }

  @ApiStatus.Experimental
  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks(@NotNull Project project) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getAllSdks(project);
  }

  @ApiStatus.Experimental
  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks(@NotNull PyProject pyProject) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getAllSdks(pyProject);
  }

  /**
   * The SDK of the interpreter of the Python project on {@code module}, read from the project structure. {@code null}
   * before the first structure lands.
   *
   * @deprecated Use the interpreter of the Python project, {@code PyProject.getInterpreter}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @Nullable Sdk findPythonSdk(@Nullable Module module) {
    return module == null ? null : MainPythonSdkKt.pythonSdkIfReady(module);
  }

  public static boolean isRemote(@Nullable Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote(sdk);
  }


  @SuppressWarnings("unused") // Used by ext. plugin
  public static @NlsSafe String getUserSite() {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getUserSite();
  }

  public static @Nullable VirtualFile findSkeletonsDir(final @NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.findSkeletonsDir(sdk);
  }

  public static boolean isDisposed(@NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isDisposed(sdk);
  }

  // It is only here for external plugins
  @RequiresBackgroundThread(generateAssertion = false)
  @SuppressWarnings("unused") // Used by ext. plugin
  public static @Nullable String getPythonExecutable(@NotNull String rootPath) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getPythonExecutable(rootPath);
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
  @SuppressWarnings("unused") // Used by ext. plugin
  public static @Nullable VirtualFile getSitePackagesDirectory(@NotNull Sdk pythonSdk) {
    return PySkeletonUtil.getSitePackagesDirectory(pythonSdk);
  }

  /**
   * @deprecated use {@link PythonInterpreter}
   */
  @Deprecated
  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean isVirtualEnv(@NotNull Sdk sdk) {
    return VENV_KIND_ID.equals(kindIdOf(sdk));
  }

  /**
   * @deprecated use {@link PythonInterpreter}
   */
  @Deprecated
  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean isCondaVirtualEnv(@NotNull Sdk sdk) {
    return CONDA_KIND_ID.equals(kindIdOf(sdk));
  }

  /** The id its provider declares in xml, or {@code null} when no environment was detected. */
  private static @Nullable String kindIdOf(@NotNull Sdk sdk) {
    PythonInterpreter interpreter = PythonInterpreterKt.pythonInterpreterResult(sdk, false).getSuccessOrNull();
    PythonEnvironment environment = interpreter == null ? null : PythonInterpreterKt.getDetectedEnvironment(interpreter);
    return environment == null ? null : PythonEnvironmentExtKt.getKindId(environment);
  }
}
