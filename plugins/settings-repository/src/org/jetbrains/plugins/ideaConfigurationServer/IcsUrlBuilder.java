package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.SystemInfo;
import org.apache.commons.httpclient.NameValuePair;

public abstract class IcsUrlBuilder {
  private static final String WINDOWS = "windows";
  private static final String OS2 = "os2";
  private static final String MAC = "mac";
  private static final String FREEBSD = "freebsd";
  private static final String LINUX = "linux";
  private static final String UNIX = "unix";
  private static final String UNKNOWN = "unknown";

  private static final String ALTERNATIVE_URL = System.getProperty("idea.server.alternative.url");
  private static final String ourIdeaServerUrl = ALTERNATIVE_URL != null ? ALTERNATIVE_URL : "http://configr.jetbrains.com";

  private final String filePath;
  private final RoamingType roamingType;
  private final String projectKey;

  public IcsUrlBuilder(final String filePath, final RoamingType roamingType, String projectKey) {
    this.filePath = filePath;
    this.roamingType = roamingType;
    this.projectKey = projectKey;
  }

  private static String getPlatformName() {
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

  public String getServerUrl() {
    return ourIdeaServerUrl;
  }

  public NameValuePair[] getQueryString() {
    if (roamingType != RoamingType.GLOBAL) {
      return new NameValuePair[]{
        new NameValuePair("path", buildPath())
      };
    }
    else {
      return new NameValuePair[]{
        new NameValuePair("path", buildPath()),
        new NameValuePair("global", "true")
      };
    }
  }

  public NameValuePair[] getPingQueryString() {
    return new NameValuePair[]{
    };
  }

  public String buildPath() {
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

  public abstract void setDisconnectedStatus();

  public abstract void setUnauthorizedStatus();

  public RoamingType getRoamingType() {
    return roamingType;
  }
}