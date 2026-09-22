// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.updater.Runner.LOG;

/**
 * Windows native helpers for Java versions before 22.
 * The Java 22 multi-release class provides the FFM implementation.
 */
public final class WindowsNative {
  public static final int MIN_FEATURE = 22;

  /** Predefined hive handle. */
  static final long HKEY_CURRENT_USER = 0xFFFFFFFF80000001L;
  /** Predefined hive handle. */
  static final long HKEY_LOCAL_MACHINE = 0xFFFFFFFF80000002L;

  /**
   * Returns a supplier of {@code null}, because this Java version cannot use the Windows native helpers.
   * The first call logs the reason on Windows.
   */
  public static @NotNull Supplier<@Nullable WindowsNative> supplier() {
    return () -> Holder.INSTANCE;
  }

  private static final class Holder {
    static final @Nullable WindowsNative INSTANCE = load();

    private static @Nullable WindowsNative load() {
      if (Utils.IS_WINDOWS) {
        LOG.info("Windows native helpers need Java " + MIN_FEATURE + "+; the feature is skipped");
      }
      return null;
    }
  }

  private WindowsNative() { }

  @NotNull List<NativeFileManager.Process> processesUsing(@NotNull Path file, int initialBufferSize) {
    return List.of();
  }

  boolean terminateProcess(int pid, @NotNull String name) {
    return false;
  }

  @NotNull String @NotNull [] registrySubKeys(long rootKey, @NotNull String key) throws NativeException {
    return new String[0];
  }

  @Nullable String registryGetString(long rootKey, @NotNull String key, @NotNull String value) throws NativeException {
    return null;
  }

  void registrySetString(long rootKey, @NotNull String key, @NotNull String value, @NotNull String data) throws NativeException { }

  void registryCreateKey(long rootKey, @NotNull String key) throws NativeException { }

  void registryCopyValues(long rootKey, @NotNull String from, @NotNull String to) throws NativeException { }

  void registryDeleteKey(long rootKey, @NotNull String key) throws NativeException { }

  void notifyShellAssociationsChanged() { }

  @Nullable Path folderPath(int csidl) {
    return null;
  }

  /** A Win32 {@code LSTATUS} failure. {@link #errorCode} is the status. */
  static final class NativeException extends Exception {
    final int errorCode;

    NativeException(@NotNull String function, int errorCode) {
      super(function + " failed with Win32 error " + errorCode);
      this.errorCode = errorCode;
    }
  }
}