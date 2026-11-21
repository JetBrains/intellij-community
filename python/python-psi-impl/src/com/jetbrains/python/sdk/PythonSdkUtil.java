// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.jetbrains.python.psi.resolve.MainPythonSdkKt;
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

  public static boolean isPythonSdk(@NotNull Sdk sdk) {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.isPythonSdk(sdk);
  }

  /**
   * @deprecated Do not use SDK, it will be dropped soon
   */
  @Deprecated(forRemoval = true)
  public static @Unmodifiable @NotNull List<@NotNull Sdk> getAllSdks() {
    return com.jetbrains.python.sdk.legacy.PythonSdkUtil.getAllSdks();
  }

  /**
   * The SDK of the interpreter of the Python project on {@code module}, read from the project structure. {@code null}
   * before the first structure lands.
   *
   * @deprecated Use the interpreter of the Python project, {@code PyProject.getInterpreter}.
   */
  @Deprecated(forRemoval = true)
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
}
