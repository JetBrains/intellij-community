/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.SystemProperties;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.IdeaSVNConfigFile;
import org.jetbrains.idea.svn.SSLExceptionsHelper;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.config.ProxyGroup;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNCompositeConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.File;
import java.util.Map;
import java.util.StringTokenizer;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class SvnAuthenticationManager {

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
  private SvnAuthenticationInteraction myInteraction;
  private AuthenticationProvider myProvider;

  public SvnAuthenticationManager(@NotNull SvnVcs vcs, final File configDirectory) {
    myVcs = vcs;
    myProject = myVcs.getProject();
    myConfigDirectory = configDirectory;
    myConfig = myVcs.getSvnConfiguration();
    myInteraction = new MySvnAuthenticationInteraction(myProject);
    Disposer.register(myProject, () -> {
      myVcs = null;
      myProject = null;
      if (myInteraction instanceof MySvnAuthenticationInteraction) {
        ((MySvnAuthenticationInteraction)myInteraction).myProject = null;
      }
      if (myConfig != null) {
        myConfig.clear();
        myConfig = null;
      }
      myInteraction = null;
    });
  }

  public SVNAuthentication requestFromCache(String kind, String realm) {
    return (SVNAuthentication)SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck(kind, realm);
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
  public HostOptions getHostOptions(@NotNull SVNURL url) {
    return new HostOptions(url);
  }

  public void acknowledgeAuthentication(String kind, String realm, SVNAuthentication authentication) {
    acknowledgeAuthentication(kind, realm, authentication, null);
  }

  public void acknowledgeAuthentication(String kind, String realm, SVNAuthentication authentication, SVNURL url) {
    SSLExceptionsHelper.removeInfo();
    if (url != null) {
      CommonProxy.getInstance().removeNoProxy(url.getProtocol(), url.getHost(), url.getPort());
    }
    final boolean authStorageEnabled = getHostOptions(authentication.getURL()).isAuthStorageEnabled();
    final SVNAuthentication proxy = ProxySvnAuthentication.proxy(authentication, authStorageEnabled);
    getConfig().acknowledge(kind, realm, proxy);
  }

  private final static int DEFAULT_READ_TIMEOUT = 30 * 1000;
  private final static int DEFAULT_CONNECT_TIMEOUT = 60 * 1000;

  public int getReadTimeout(@NotNull SVNURL url) {
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

  public int getConnectTimeout(@NotNull SVNURL url) {
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
    final SVNURL svnurl;
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

  private static class MySvnAuthenticationInteraction implements SvnAuthenticationInteraction {
    private Project myProject;

    private MySvnAuthenticationInteraction(Project project) {
      myProject = project;
    }

    @Override
    public void warnOnAuthStorageDisabled(SVNURL url) {
      VcsBalloonProblemNotifier
        .showOverChangesView(myProject, "Cannot store credentials: forbidden by \"store-auth-creds=no\"", MessageType.ERROR);
    }

    @Override
    public void warnOnPasswordStorageDisabled(SVNURL url) {
      VcsBalloonProblemNotifier
        .showOverChangesView(myProject, "Cannot store password: forbidden by \"store-passwords=no\"", MessageType.ERROR);
    }

    @Override
    public void warnOnSSLPassphraseStorageDisabled(SVNURL url) {
      VcsBalloonProblemNotifier
        .showOverChangesView(myProject, "Cannot store passphrase: forbidden by \"store-ssl-client-cert-pp=no\"", MessageType.ERROR);
    }

    @Override
    public boolean promptForPlaintextPasswordSaving(SVNURL url, String realm) {
      final int answer = Messages.showYesNoDialog(myProject, String.format("Your password for authentication realm:\n" +
                                                                           "%s\ncan only be stored to disk unencrypted. Would you like to store it in plaintext?",
                                                                           realm),
                                                  "Store the password in plaintext?", Messages.getQuestionIcon());
      return answer == Messages.YES;
    }

    @Override
    public boolean promptInAwt() {
      return true;
    }

    @Override
    public boolean promptForSSLPlaintextPassphraseSaving(SVNURL url, String realm, File certificateFile, String certificateName) {
      final int answer = Messages.showYesNoDialog(myProject,
                                                  String.format("Your passphrase for " +
                                                                certificateName +
                                                                ":\n%s\ncan only be stored to disk unencrypted. Would you like to store it in plaintext?",
                                                                certificateFile.getPath()),
                                                  "Store the passphrase in plaintext?", Messages.getQuestionIcon());
      return answer == Messages.YES;
    }

    @Override
    public void dispose() {
      myProject = null;
    }
  }

  public class HostOptions {
    @NotNull private final SVNURL myUrl;

    private HostOptions(@NotNull SVNURL url) {
      myUrl = url;
    }

    public boolean isAuthStorageEnabled() {
      String perHostValue = getPropertyIdea(myUrl.getHost(), myServersFile.getValue(), "store-auth-creds");
      boolean storageEnabled =
        perHostValue != null ? isTurned(perHostValue) : isTurned(myConfigFile.getValue().getPropertyValue("auth", "store-auth-creds"));

      if (!storageEnabled) {
        myInteraction.warnOnAuthStorageDisabled(myUrl);
      }

      return storageEnabled;
    }

    @Nullable
    public String getSSLClientCertFile() {
      return getPropertyIdea(myUrl.getHost(), myServersFile.getValue(), "ssl-client-cert-file");
    }
  }
}
