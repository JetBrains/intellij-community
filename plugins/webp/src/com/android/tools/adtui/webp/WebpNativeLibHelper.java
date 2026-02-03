/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.webp;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WebpNativeLibHelper {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private static boolean sJniLibLoaded;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private static boolean sJniLibLoadAttempted;

  private WebpNativeLibHelper() { }

  static void requireNativeLibrary() throws IOException {
    if (!loadNativeLibraryIfNeeded()) {
      throw new IOException("The WebP decoder library could not be loaded");
    }
  }

  public static @NotNull String getDecoderVersion() {
    // A decoded result of calling `WebPGetDecoderVersion()`; we don't want to load the native library just to get this constant.
    return "1.3.2";
  }

  public static @NotNull String getEncoderVersion() {
    return getDecoderVersion();
  }

  public static boolean loadNativeLibraryIfNeeded() {
    if (!sJniLibLoadAttempted) {
      try {
        loadNativeLibrary();
      }
      catch (UnsatisfiedLinkError e) {
        Logger.getInstance(WebpNativeLibHelper.class).warn(e);
      }
    }
    return sJniLibLoaded;
  }

  private static synchronized void loadNativeLibrary() {
    if (sJniLibLoadAttempted) {
      // Already attempted to load, nothing to do here.
      return;
    }
    try {
      var libFile = getLibLocation();
      if (libFile == null) throw new UnsatisfiedLinkError("WebP JNI binding is missing");
      if (!Files.exists(libFile)) throw new UnsatisfiedLinkError(String.format("'%1$s' does not exist", libFile));
      System.load(libFile.toString());
    }
    finally {
      sJniLibLoadAttempted = true;
    }
    sJniLibLoaded = true;
  }

  public static @Nullable Path getLibLocation() {
    String platformName;
    if (SystemInfo.isWindows) platformName = "win";
    else if (SystemInfo.isMac) platformName = "mac";
    else if (SystemInfo.isLinux) platformName = "linux";
    else return null;
    var relativePath = "lib/libwebp/" + platformName + '/' + System.mapLibraryName("webp_jni");

    // A terrible hack for dev environment.
    var local = Path.of(PluginPathManager.getPluginHomePath("webp"), relativePath);
    if (Files.exists(local)) return local;

    var resource = PluginPathManager.getPluginResource(WebpNativeLibHelper.class, relativePath);
    return resource != null ? resource.toPath().toAbsolutePath() : null;
  }
}
