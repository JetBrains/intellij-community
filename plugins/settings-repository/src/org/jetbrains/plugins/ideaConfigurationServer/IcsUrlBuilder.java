package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IcsUrlBuilder {
  public static final String PROJECTS_DIR_NAME = "_projects/";

  private static String getOsFolderName() {
    if (SystemInfo.isWindows) {
      return "_windows";
    }
    else if (SystemInfo.isMac) {
      return "_mac";
    }
    else if (SystemInfo.isLinux) {
      return "_linux";
    }
    else if (SystemInfo.isFreeBSD) {
      return "_freebsd";
    }
    else if (SystemInfo.isUnix) {
      return "_unix";
    }
    return "_unknown";
  }

  @NotNull
  static String buildPath(@NotNull String filePath, @NotNull RoamingType roamingType, @Nullable String projectKey) {
    if (projectKey != null) {
      return PROJECTS_DIR_NAME + projectKey + '/' + filePath;
    }
    else if (roamingType == RoamingType.PER_PLATFORM) {
      return getOsFolderName() + '/' + filePath;
    }
    else {
      return filePath;
    }
  }
}