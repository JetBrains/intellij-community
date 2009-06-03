package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class IgnoredBeanFactory {
  private IgnoredBeanFactory() {
  }

  public static IgnoredFileBean ignoreUnderDirectory(final @NonNls String path, Project p) {
    final String correctedPath = (path.endsWith("/") || path.endsWith(File.separator)) ? path : path + "/";
    return new IgnoredFileBean(correctedPath, IgnoreSettingsType.UNDER_DIR, p);
  }

  public static IgnoredFileBean ignoreFile(final @NonNls String path, Project p) {
    // todo check??
    return new IgnoredFileBean(path, IgnoreSettingsType.FILE, p);
  }

  public static IgnoredFileBean withMask(final String mask) {
    return new IgnoredFileBean(mask);
  }
}
