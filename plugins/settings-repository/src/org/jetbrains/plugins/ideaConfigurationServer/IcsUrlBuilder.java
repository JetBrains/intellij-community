package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.SystemInfo;

public final class IcsUrlBuilder {
  public static final String PROJECTS_DIR_NAME = "projects/";

  private static String getOsName() {
    if (SystemInfo.isWindows) {
      return "windows";
    }
    else if (SystemInfo.isMac) {
      return "mac";
    }
    else if (SystemInfo.isLinux) {
      return "linux";
    }
    else if (SystemInfo.isFreeBSD) {
      return "freebsd";
    }
    else if (SystemInfo.isUnix) {
      return "unix";
    }
    return "unknown";
  }

  static String buildPath(String filePath, RoamingType roamingType, String projectKey) {
    StringBuilder result = new StringBuilder();
    if (projectKey != null) {
      result.append(PROJECTS_DIR_NAME).append(projectKey).append('/');
    }
    else if (roamingType == RoamingType.PER_PLATFORM) {
      result.append("os/").append(getOsName()).append('/');
    }
    result.append(filePath);
    return result.toString();
  }
}