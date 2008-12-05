package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.NonNls;

import java.io.File;

public class IgnoredBeanFactory {
  public static IgnoredFileBean ignoreUnderDirectory(final @NonNls String path) {
    final String correctedPath = (path.endsWith("/") || path.endsWith(File.separator)) ? path : path + "/";
    return new IgnoredFileBean(correctedPath, IgnoreSettingsType.UNDER_DIR);
  }

  public static IgnoredFileBean ignoreFile(final @NonNls String path) {
    // todo check??
    return new IgnoredFileBean(path, IgnoreSettingsType.FILE);
  }

  public static IgnoredFileBean withMask(final String mask) {
    return new IgnoredFileBean(mask);
  }
}
