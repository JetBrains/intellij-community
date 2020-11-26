// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.config.ServersFileKeys;
import org.jetbrains.idea.svn.config.SvnIniFile;

import java.nio.file.Path;

import static com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.SYSTEM_CONFIGURATION_PATH;
import static org.jetbrains.idea.svn.config.SvnIniFile.*;

public final class SvnAuthenticationManager {
  // TODO Looks reasonable to introduce some AuthType/AuthKind class
  public static final @NonNls String PASSWORD = "svn.simple";
  public static final @NonNls String SSL = "svn.ssl.client-passphrase";

  public static final @NonNls String SVN_SSH = "svn+ssh";
  public static final @NonNls String HTTP = "http";
  public static final @NonNls String HTTPS = "https";

  private final @NotNull Project myProject;
  private final @NotNull Path myConfigDirectory;
  private final NotNullLazyValue<Couple<SvnIniFile>> myConfigFile;
  private final NotNullLazyValue<Couple<SvnIniFile>> myServersFile;
  private AuthenticationProvider myProvider;

  public SvnAuthenticationManager(@NotNull Project project, @NotNull Path configDirectory) {
    myProject = project;
    myConfigDirectory = configDirectory;
    myConfigFile = NotNullLazyValue.lazy(() -> {
        SvnIniFile userConfig = new SvnIniFile(myConfigDirectory.resolve(CONFIG_FILE_NAME));
        SvnIniFile systemConfig = new SvnIniFile(SYSTEM_CONFIGURATION_PATH.getValue().resolve(CONFIG_FILE_NAME));
        return Couple.of(systemConfig, userConfig);
      });
    myServersFile = NotNullLazyValue.lazy(() -> {
        SvnIniFile userConfig = new SvnIniFile(myConfigDirectory.resolve(SERVERS_FILE_NAME));
        SvnIniFile systemConfig = new SvnIniFile(SYSTEM_CONFIGURATION_PATH.getValue().resolve(SERVERS_FILE_NAME));
        return Couple.of(systemConfig, userConfig);
      });
  }

  public AuthenticationData requestFromCache(String kind, String realm) {
    return (AuthenticationData)SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck(kind, realm);
  }

  public String getDefaultUsername() {
    return SystemProperties.getUserName();
  }

  public void setAuthenticationProvider(AuthenticationProvider provider) {
    myProvider = provider;
  }

  public AuthenticationProvider getProvider() {
    return myProvider;
  }

  @NotNull
  public HostOptions getHostOptions(@NotNull Url url) {
    return new HostOptions(url);
  }

  public void acknowledgeAuthentication(String kind, Url url, String realm, AuthenticationData authentication) {
    boolean authStorageEnabled = getHostOptions(url).isAuthStorageEnabled();
    AuthenticationData proxy = ProxySvnAuthentication.proxy(authentication, authStorageEnabled);
    SvnConfiguration.getInstance(myProject).acknowledge(kind, realm, proxy);
  }

  private final static int DEFAULT_READ_TIMEOUT = 30 * 1000;
  private final static int DEFAULT_CONNECT_TIMEOUT = 60 * 1000;

  public int getReadTimeout(@NotNull Url url) {
    String protocol = url.getProtocol();
    if (HTTP.equals(protocol) || HTTPS.equals(protocol)) {
      String host = url.getHost();
      String timeout = getPropertyIdea(host, myServersFile.getValue(), ServersFileKeys.TIMEOUT);
      if (timeout != null) {
        try {
          return Integer.parseInt(timeout) * 1000;
        }
        catch (NumberFormatException nfe) {
          // use default
        }
      }
      return DEFAULT_READ_TIMEOUT;
    }
    if (SVN_SSH.equals(protocol)) {
      return (int)SvnConfiguration.getInstance(myProject).getSshReadTimeout();
    }
    return 0;
  }

  public int getConnectTimeout(@NotNull Url url) {
    String protocol = url.getProtocol();
    if (SVN_SSH.equals(protocol)) {
      return (int)SvnConfiguration.getInstance(myProject).getSshConnectionTimeout();
    }

    return HTTP.equals(protocol) || HTTPS.equals(protocol) ? DEFAULT_CONNECT_TIMEOUT : 0;
  }

  public void warnOnAuthStorageDisabled() {
    showOverChangesView(myProject, message("svn.cannot.save.credentials.store-auth-creds"), MessageType.ERROR);
  }

  public final class HostOptions {
    @NotNull private final Url myUrl;

    private HostOptions(@NotNull Url url) {
      myUrl = url;
    }

    public boolean isAuthStorageEnabled() {
      String perHostValue = getPropertyIdea(myUrl.getHost(), myServersFile.getValue(), "store-auth-creds");
      boolean storageEnabled =
        perHostValue != null
        ? isTurned(perHostValue, false)
        : isTurned(getValue(myConfigFile.getValue(), "auth", "store-auth-creds"), true);

      if (!storageEnabled) {
        warnOnAuthStorageDisabled();
      }

      return storageEnabled;
    }

    @Nullable
    public String getSSLClientCertFile() {
      return getPropertyIdea(myUrl.getHost(), myServersFile.getValue(), "ssl-client-cert-file");
    }
  }
}
