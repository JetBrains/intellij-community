package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.SystemInfo;

final class IcsUrlBuilder {
  private static final String WINDOWS = "windows";
  private static final String OS2 = "os2";
  private static final String MAC = "mac";
  private static final String FREEBSD = "freebsd";
  private static final String LINUX = "linux";
  private static final String UNIX = "unix";
  private static final String UNKNOWN = "unknown";

  static String getPlatformName() {
    if (SystemInfo.isWindows) {
      return WINDOWS;
    }
    if (SystemInfo.isOS2) return OS2;
    if (SystemInfo.isMac) return MAC;
    if (SystemInfo.isFreeBSD) return FREEBSD;
    if (SystemInfo.isLinux) return LINUX;
    if (SystemInfo.isUnix) return UNIX;

    return UNKNOWN;
  }

  static String buildPath(String filePath, RoamingType roamingType, String projectKey) {
    StringBuilder result = new StringBuilder();
    if (projectKey != null) {
      result.append("projects/").append(projectKey).append("/");
    }
    if (roamingType == RoamingType.PER_USER) {
      result.append(filePath);
    }
    else if (roamingType == RoamingType.PER_PLATFORM) {
      result.append("platforms/").append(getPlatformName()).append("/").append(filePath);
    }
    else if (roamingType == RoamingType.GLOBAL) {
      result.append("$GLOBAL$/").append(filePath);
    }
    else {
      result.append(filePath);
    }
    return result.toString();
  }
}