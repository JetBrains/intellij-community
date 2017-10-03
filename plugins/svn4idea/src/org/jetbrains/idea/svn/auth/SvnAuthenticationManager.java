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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.*;

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
  private SvnConfiguration myConfig;
  private SvnAuthenticationInteraction myInteraction;
  private IdeaSVNHostOptionsProvider myLocalHostOptionsProvider;
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

  public IdeaSVNHostOptionsProvider getHostOptionsProvider() {
    if (myLocalHostOptionsProvider == null) {
      myLocalHostOptionsProvider = new IdeaSVNHostOptionsProvider();
    }
    return myLocalHostOptionsProvider;
  }

  public void acknowledgeAuthentication(String kind, String realm, SVNAuthentication authentication) {
    acknowledgeAuthentication(kind, realm, authentication, null);
  }

  public void acknowledgeAuthentication(String kind, String realm, SVNAuthentication authentication, SVNURL url) {
    SSLExceptionsHelper.removeInfo();
    if (url != null) {
      CommonProxy.getInstance().removeNoProxy(url.getProtocol(), url.getHost(), url.getPort());
    }
    final boolean authStorageEnabled = getHostOptionsProvider().getHostOptions(authentication.getURL()).isAuthStorageEnabled();
    final SVNAuthentication proxy = ProxySvnAuthentication.proxy(authentication, authStorageEnabled);
    getConfig().acknowledge(kind, realm, proxy);
  }

  // 30 seconds
  private final static int DEFAULT_READ_TIMEOUT = 30 * 1000;

  public int getReadTimeout(@NotNull SVNURL url) {
    String protocol = url.getProtocol();
    if (HTTP.equals(protocol) || HTTPS.equals(protocol)) {
      String host = url.getHost();
      String timeout = getServersPropertyIdea(host, "http-timeout");
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
    final int connectTimeout = getHostOptionsProvider().getHostOptions(url).getConnectTimeout();
    if ((HTTP.equals(protocol) || HTTPS.equals(protocol)) && (connectTimeout <= 0)) {
      return DEFAULT_READ_TIMEOUT;
    }
    return connectTimeout;
  }

  // taken from default manager as is
  private String getServersPropertyIdea(String host, final String name) {
    final SVNCompositeConfigFile serversFile = getHostOptionsProvider().getServersFile();
    return getPropertyIdea(host, serversFile, name);
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

  public class IdeaSVNHostOptionsProvider extends DefaultSVNHostOptionsProvider {
    public IdeaSVNHostOptionsProvider() {
      super(myConfigDirectory);
    }

    @Override
    public SVNCompositeConfigFile getServersFile() {
      return super.getServersFile();
    }

    @Override
    public ISVNHostOptions getHostOptions(SVNURL url) {
      return new IdeaSVNHostOptions(getServersFile(), url);
    }
  }

  private class IdeaSVNHostOptions extends DefaultSVNHostOptions {
    private SVNCompositeConfigFile myConfigFile;
    private final SVNURL myUrl;

    private IdeaSVNHostOptions(SVNCompositeConfigFile serversFile, SVNURL url) {
      super(serversFile, url);
      myUrl = url;
    }

    @Override
    public boolean isStorePlainTextPasswords(final String realm, SVNAuthentication auth) throws SVNException {
      return ISVNAuthenticationManager.USERNAME.equals(auth.getKind()) || super.isStorePlainTextPasswords(realm, auth);
    }

    @Override
    public boolean isStorePlainTextPassphrases(final String realm, final SVNAuthentication auth) throws SVNException {
      return ISVNAuthenticationManager.USERNAME.equals(auth.getKind()) || super.isStorePlainTextPassphrases(realm, auth);
    }

    @Override
    public boolean isAuthStorageEnabled() {
      final boolean value;
      if (hasAuthStorageEnabledOption()) {
        value = super.isAuthStorageEnabled();
      }
      else {
        value = isTurned(getConfigFile().getPropertyValue("auth", "store-auth-creds"));
      }
      if (!value) {
        myInteraction.warnOnAuthStorageDisabled(myUrl);
      }
      return value;
    }

    @Override
    public boolean isStorePasswords() {
      final String storePasswords = getServersPropertyIdea(getHost(), "store-passwords");
      final boolean value;
      if (storePasswords != null) {
        value = isTurned(storePasswords);
      }
      else {
        final String configValue = getConfigFile().getPropertyValue("auth", "store-passwords");
        value = isTurned(configValue);
      }
      if (!value) {
        myInteraction.warnOnPasswordStorageDisabled(myUrl);
      }
      return value;
    }

    @Override
    public boolean isStoreSSLClientCertificatePassphrases() {
      final boolean value = super.isStoreSSLClientCertificatePassphrases();
      if (!value) {
        myInteraction.warnOnSSLPassphraseStorageDisabled(myUrl);
      }
      return value;
    }

    private SVNCompositeConfigFile getConfigFile() {
      if (myConfigFile == null) {
        final File config = new File(myConfigDirectory, "config");
        SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
        SVNConfigFile userConfig = new SVNConfigFile(config);
        SVNConfigFile systemConfig = new SVNConfigFile(new File(SVNFileUtil.getSystemConfigurationDirectory(), "config"));
        myConfigFile = new SVNCompositeConfigFile(systemConfig, userConfig);
      }
      return myConfigFile;
    }
  }
}
