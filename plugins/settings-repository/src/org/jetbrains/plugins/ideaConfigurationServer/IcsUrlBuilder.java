package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.SystemInfo;

final class IcsUrlBuilder {
  private static String getOsName() {
    if (SystemInfo.isWindows) {
      return "windows";
    }
    if (SystemInfo.isOS2) {
      return "os2";
    }
    if (SystemInfo.isMac) {
      return "mac";
    }
    if (SystemInfo.isLinux) {
      return "linux";
    }
    if (SystemInfo.isOS2) {
      return "os2";
    }
    if (SystemInfo.isFreeBSD) {
      return "freebsd";
    }
    if (SystemInfo.isUnix) {
      return "unix";
    }
    return "unknown";
  }

  static String buildPath(String filePath, RoamingType roamingType, String projectKey) {
    StringBuilder result = new StringBuilder();
    if (projectKey != null) {
      result.append("_projects/").append(projectKey).append('/');
    }
    else if (roamingType == RoamingType.PER_PLATFORM) {
      result.append("_os/").append(getOsName()).append('/');
    }
    else if (roamingType == RoamingType.GLOBAL) {
      result.append("_global/");
    }
    result.append(filePath);
    return result.toString();
  }
}