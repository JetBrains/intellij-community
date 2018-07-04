// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.IdeaSVNConfigFile;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;

import java.nio.file.Path;

import static org.jetbrains.idea.svn.IdeaSVNConfigFile.*;
import static org.jetbrains.idea.svn.SvnUtil.SYSTEM_CONFIGURATION_PATH;

public class SvnAuthenticationManager {

  // TODO Looks reasonable to introduce some AuthType/AuthKind class
  public static final String PASSWORD = "svn.simple";
  public static final String SSL = "svn.ssl.client-passphrase";

  public static final String SVN_SSH = "svn+ssh";
  public static final String HTTP = "http";
  public static final String HTTPS = "https";
  private SvnVcs myVcs;
  private Project myProject;
  @NotNull private final Path myConfigDirectory;
  private final NotNullLazyValue<Couple<IdeaSVNConfigFile>> myConfigFile = new NotNullLazyValue<Couple<IdeaSVNConfigFile>>() {
    @NotNull
    @Override
    protected Couple<IdeaSVNConfigFile> compute() {
      IdeaSVNConfigFile userConfig = new IdeaSVNConfigFile(myConfigDirectory.resolve(CONFIG_FILE_NAME));
      IdeaSVNConfigFile systemConfig = new IdeaSVNConfigFile(SYSTEM_CONFIGURATION_PATH.getValue().resolve(CONFIG_FILE_NAME));
      return Couple.of(systemConfig, userConfig);
    }
  };
  private final NotNullLazyValue<Couple<IdeaSVNConfigFile>> myServersFile = new NotNullLazyValue<Couple<IdeaSVNConfigFile>>() {
    @NotNull
    @Override
    protected Couple<IdeaSVNConfigFile> compute() {
      IdeaSVNConfigFile userConfig = new IdeaSVNConfigFile(myConfigDirectory.resolve(SERVERS_FILE_NAME));
      IdeaSVNConfigFile systemConfig = new IdeaSVNConfigFile(SYSTEM_CONFIGURATION_PATH.getValue().resolve(SERVERS_FILE_NAME));
      return Couple.of(systemConfig, userConfig);
    }
  };
  private SvnConfiguration myConfig;
  private AuthenticationProvider myProvider;

  public SvnAuthenticationManager(@NotNull SvnVcs vcs, @NotNull Path configDirectory) {
    myVcs = vcs;
    myProject = myVcs.getProject();
    myConfigDirectory = configDirectory;
    myConfig = myVcs.getSvnConfiguration();
    Disposer.register(myProject, () -> {
      myVcs = null;
      myProject = null;
      if (myConfig != null) {
        myConfig.clear();
        myConfig = null;
      }
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

  // since set to null during dispose and we have background processes
  private SvnConfiguration getConfig() {
    if (myConfig == null) throw new ProcessCanceledException();
    return myConfig;
  }

  @NotNull
  public HostOptions getHostOptions(@NotNull Url url) {
    return new HostOptions(url);
  }

  public void acknowledgeAuthentication(String kind, Url url, String realm, AuthenticationData authentication) {
    boolean authStorageEnabled = getHostOptions(url).isAuthStorageEnabled();
    AuthenticationData proxy = ProxySvnAuthentication.proxy(authentication, authStorageEnabled);
    getConfig().acknowledge(kind, realm, proxy);
  }

  private final static int DEFAULT_READ_TIMEOUT = 30 * 1000;
  private final static int DEFAULT_CONNECT_TIMEOUT = 60 * 1000;

  public int getReadTimeout(@NotNull Url url) {
    String protocol = url.getProtocol();
    if (HTTP.equals(protocol) || HTTPS.equals(protocol)) {
      String host = url.getHost();
      String timeout = getPropertyIdea(host, myServersFile.getValue(), "http-timeout");
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
      return (int)getConfig().getSshReadTimeout();
    }
    return 0;
  }

  public int getConnectTimeout(@NotNull Url url) {
    String protocol = url.getProtocol();
    if (SVN_SSH.equals(protocol)) {
      return (int)getConfig().getSshConnectionTimeout();
    }

    return HTTP.equals(protocol) || HTTPS.equals(protocol) ? DEFAULT_CONNECT_TIMEOUT : 0;
  }

  public void warnOnAuthStorageDisabled() {
    VcsBalloonProblemNotifier
      .showOverChangesView(myProject, "Cannot store credentials: forbidden by \"store-auth-creds=no\"", MessageType.ERROR);
  }

  public class HostOptions {
    @NotNull private final Url myUrl;

    private HostOptions(@NotNull Url url) {
      myUrl = url;
    }

    public boolean isAuthStorageEnabled() {
      String perHostValue = getPropertyIdea(myUrl.getHost(), myServersFile.getValue(), "store-auth-creds");
      boolean storageEnabled =
        perHostValue != null ? isTurned(perHostValue) : isTurned(getValue(myConfigFile.getValue(), "auth", "store-auth-creds"));

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
