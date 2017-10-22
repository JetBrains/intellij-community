// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
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
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.config.ProxyGroup;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNCompositeConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.File;
import java.util.Map;
import java.util.StringTokenizer;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class SvnAuthenticationManager {

  // TODO Looks reasonable to introduce some AuthType/AuthKind class
  public static final String PASSWORD = "svn.simple";
  public static final String SSL = "svn.ssl.client-passphrase";

  public static final String SVN_SSH = "svn+ssh";
  public static final String HTTP = "http";
  public static final String HTTPS = "https";
  private SvnVcs myVcs;
  private Project myProject;
  private File myConfigDirectory;
  private final NotNullLazyValue<SVNCompositeConfigFile> myConfigFile = new NotNullLazyValue<SVNCompositeConfigFile>() {
    @NotNull
    @Override
    protected SVNCompositeConfigFile compute() {
      SVNConfigFile userConfig = new SVNConfigFile(new File(myConfigDirectory, "config"));
      SVNConfigFile systemConfig = new SVNConfigFile(new File(SVNFileUtil.getSystemConfigurationDirectory(), "config"));
      return new SVNCompositeConfigFile(systemConfig, userConfig);
    }
  };
  private final NotNullLazyValue<SVNCompositeConfigFile> myServersFile = new NotNullLazyValue<SVNCompositeConfigFile>() {
    @NotNull
    @Override
    protected SVNCompositeConfigFile compute() {
      SVNConfigFile userConfig = new SVNConfigFile(new File(myConfigDirectory, "servers"));
      SVNConfigFile systemConfig = new SVNConfigFile(new File(SVNFileUtil.getSystemConfigurationDirectory(), "servers"));
      return new SVNCompositeConfigFile(systemConfig, userConfig);
    }
  };
  private SvnConfiguration myConfig;
  private AuthenticationProvider myProvider;

  public SvnAuthenticationManager(@NotNull SvnVcs vcs, final File configDirectory) {
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

  private static String getPropertyIdea(String host, SVNCompositeConfigFile serversFile, final String name) {
    String groupName = getGroupName(serversFile.getProperties("groups"), host);
    if (groupName != null) {
      Map hostProps = serversFile.getProperties(groupName);
      final String value = (String)hostProps.get(name);
      if (value != null) {
        return value;
      }
    }
    Map globalProps = serversFile.getProperties("global");
    return (String)globalProps.get(name);
  }

  public static boolean checkHostGroup(final String url, final String patterns, final String exceptions) {
    final Url svnurl;
    try {
      svnurl = createUrl(url);
    }
    catch (SvnBindException e) {
      return false;
    }

    final String host = svnurl.getHost();
    return matches(patterns, host) && (!matches(exceptions, host));
  }

  private static boolean matches(final String pattern, final String host) {
    final StringTokenizer tokenizer = new StringTokenizer(pattern, ",");
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (DefaultSVNOptions.matches(token, host)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String getGroupForHost(final String host, final IdeaSVNConfigFile serversFile) {
    final Map<String, ProxyGroup> groups = serversFile.getAllGroups();
    for (Map.Entry<String, ProxyGroup> entry : groups.entrySet()) {
      if (matchesGroupPattern(host, entry.getValue().getPatterns())) return entry.getKey();
    }
    return null;
  }

  // taken from default manager as is
  private static String getGroupName(Map groups, String host) {
    for (Object o : groups.keySet()) {
      final String name = (String)o;
      final String pattern = (String)groups.get(name);
      if (matchesGroupPattern(host, pattern)) return name;
    }
    return null;
  }

  private static boolean matchesGroupPattern(String host, String pattern) {
    for (StringTokenizer tokens = new StringTokenizer(pattern, ","); tokens.hasMoreTokens(); ) {
      String token = tokens.nextToken();
      if (DefaultSVNOptions.matches(token, host)) {
        return true;
      }
    }
    return false;
  }

  // default = yes
  private static boolean isTurned(final String value) {
    return value == null || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
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
        perHostValue != null ? isTurned(perHostValue) : isTurned(myConfigFile.getValue().getPropertyValue("auth", "store-auth-creds"));

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
