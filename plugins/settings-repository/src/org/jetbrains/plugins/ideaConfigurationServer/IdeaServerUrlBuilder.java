package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.SystemInfo;
import org.apache.commons.httpclient.NameValuePair;

public abstract class IdeaServerUrlBuilder {
  private final String filePath;
  private final RoamingType roamingType;
  private final String projectKey;
  private String mySessionId;
  private final String myLogin;
  private final String myPassword;
  private boolean myReloginAutomatically = false;

  public IdeaServerUrlBuilder(final String filePath, final RoamingType roamingType, String projectKey, String sessionId,
                              String login, String password) {
    this.filePath = filePath;
    this.roamingType = roamingType;
    this.projectKey = projectKey;
    mySessionId = sessionId;
    myLogin = login;
    myPassword = password;
  }

  private static final String WINDOWS = "windows";
  private static final String OS2 = "os2";
  private static final String MAC = "mac";
  private static final String FREEBSD = "freebsd";
  private static final String LINUX = "linux";
  private static final String UNIX = "unix";
  private static final String UNKNOWN = "unknown";

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

  private static final String ALTERNATIVE_URL = System.getProperty("idea.server.alternative.url");
  private static String ourIdeaServerUrl = ALTERNATIVE_URL != null ? ALTERNATIVE_URL : "http://configr.jetbrains.com";

  public String getServerUrl() {
    return ourIdeaServerUrl;
  }

  public NameValuePair[] getQueryString() {
    if (roamingType != RoamingType.GLOBAL) {
      return new NameValuePair[]{
        new NameValuePair("session", mySessionId),
        new NameValuePair("path", buildPath())
      };
    }
    else {
      return new NameValuePair[]{
        new NameValuePair("session", mySessionId),
        new NameValuePair("path", buildPath()),
        new NameValuePair("global", "true")
      };
    }
  }

  public NameValuePair[] getPingQueryString() {
    return new NameValuePair[]{
      new NameValuePair("session", mySessionId)
    };
  }

  public NameValuePair[] getLoginQueryString() {
    return new NameValuePair[]{
      new NameValuePair("user", getLogin()),
      new NameValuePair("scrumbled-password", PasswordUtil.encodePassword(getPassword()))
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

  public IdeaServerUrlBuilder setReloginAutomatically(final boolean b) {
    myReloginAutomatically = b;
    return this;
  }

  public boolean isReloginAutomatically() {
    return myReloginAutomatically;
  }

  public String getPassword() {
    return myPassword;
  }

  public String getLogin() {
    return myLogin;
  }

  public IdeaServerUrlBuilder createLoginBuilder() {
    return new IdeaServerUrlBuilder(null, null, null, null, getLogin(), getPassword()) {
      @Override
      protected void onSessionIdUpdated(final String id) {
      }

      @Override
      public void setDisconnectedStatus() {
      }

      @Override
      public void setUnauthorizedStatus() {
      }
    };
  }

  public void updateSessionId(final String sessionId) {
    mySessionId = sessionId;
    onSessionIdUpdated(sessionId);
  }

  protected abstract void onSessionIdUpdated(final String id);

  public abstract void setDisconnectedStatus();

  public abstract void setUnauthorizedStatus();

  public RoamingType getRoamingType() {
    return roamingType;
  }
}