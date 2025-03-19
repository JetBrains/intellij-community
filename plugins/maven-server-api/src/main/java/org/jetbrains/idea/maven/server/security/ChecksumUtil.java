// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ChecksumUtil {
  public static @Nullable String checksum(@Nullable String s) {
    if (null == s) return null;
    return getMd5Hash(s);
  }

  private static @Nullable String getMd5Hash(@NotNull String string) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("MD5");
      byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
      messageDigest.update(bytes);
      return new BigInteger(1, messageDigest.digest()).toString(32);
    }
    catch (Exception e) {
      return null;
    }
  }
}
