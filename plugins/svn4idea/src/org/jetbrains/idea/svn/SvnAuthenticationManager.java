/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.util.containers.SoftHashMap;
import com.intellij.util.net.HttpConfigurable;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author alex
 */
public class SvnAuthenticationManager extends DefaultSVNAuthenticationManager {
  private final Project myProject;
  private PersistentAuthenticationProviderProxy myPersistentAuthenticationProviderProxy;

  public SvnAuthenticationManager(final Project project, final File configDirectory) {
        super(configDirectory, true, null, null);
      myProject = project;
      if (myPersistentAuthenticationProviderProxy != null) {
        myPersistentAuthenticationProviderProxy.setProject(myProject);
      }
    }

  @Override
  protected ISVNAuthenticationProvider createCacheAuthenticationProvider(File authDir, String userName) {
    myPersistentAuthenticationProviderProxy = new PersistentAuthenticationProviderProxy(super.createCacheAuthenticationProvider(authDir, userName));
    return myPersistentAuthenticationProviderProxy;
  }

  private static class PersistentAuthenticationProviderProxy implements ISVNAuthenticationProvider, IPersistentAuthenticationProvider {
    private final Map<SvnAuthWrapperEqualable, Long> myRewritePreventer;
    private static final long ourRefreshInterval = 6000 * 1000;
    private final ISVNAuthenticationProvider myDelegate;
    private Project myProject;

    private PersistentAuthenticationProviderProxy(final ISVNAuthenticationProvider delegate) {
      myDelegate = delegate;
      myRewritePreventer = new SoftHashMap<SvnAuthWrapperEqualable, Long>();
    }

    public void setProject(Project project) {
      myProject = project;
    }

    public SVNAuthentication requestClientAuthentication(final String kind, final SVNURL url, final String realm, final SVNErrorMessage errorMessage,
                                                         final SVNAuthentication previousAuth,
                                                         final boolean authMayBeStored) {
      return myDelegate.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
    }

    public int acceptServerAuthentication(final SVNURL url, final String realm, final Object certificate, final boolean resultMayBeStored) {
      return ACCEPTED_TEMPORARY;
    }

    public void saveAuthentication(final SVNAuthentication auth, final String kind, final String realm) throws SVNException {
      try {
        final SvnAuthWrapperEqualable newKey = new SvnAuthWrapperEqualable(auth);
        final Long recent = myRewritePreventer.get(newKey);
        final long currTime = System.currentTimeMillis();
        if (recent == null || ((recent != null) && ((currTime - recent.longValue()) > ourRefreshInterval))) {
          ((IPersistentAuthenticationProvider) myDelegate).saveAuthentication(auth, kind, realm);
          myRewritePreventer.put(newKey, currTime);
        }
      }
      catch (final SVNException e) {
        // show notification so that user was aware his credentials were not saved
        if (myProject == null) return;
          ApplicationManager.getApplication().invokeLater(new ChangesViewBalloonProblemNotifier(myProject,
                "<b>Problem when storing Subversion credentials:</b>&nbsp;" + e.getMessage(), MessageType.ERROR));
      }
    }
  }

  public boolean haveStoredCredentials(final String kind, final SVNURL url, final String realm, final SVNErrorMessage errorMessage,
                                                         final SVNAuthentication previousAuth) {
    final SVNAuthentication result =
      myPersistentAuthenticationProviderProxy.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, false);
    return result != null;
  }

  public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
    // this code taken from default manager (changed for system properties reading)
      String host = url.getHost();

      Map properties = getHostProperties(host);
      String proxyHost = (String) properties.get("http-proxy-host");
    if ((proxyHost == null) || "".equals(proxyHost.trim())) {
      if (SvnConfiguration.getInstanceChecked(myProject).isIsUseDefaultProxy()) {
        // ! use common proxy if it is set
        final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
        final String ideaWideProxyHost = httpConfigurable.PROXY_HOST;
        String ideaWideProxyPort = String.valueOf(httpConfigurable.PROXY_PORT);

        if (ideaWideProxyPort == null) {
          ideaWideProxyPort = "3128";
        }

        if ((ideaWideProxyHost != null) && (! "".equals(ideaWideProxyHost.trim()))) {
          return new MyPromptingProxyManager(ideaWideProxyHost, ideaWideProxyPort);
        }
      }
      return null;
    }
      String proxyExceptions = (String) properties.get("http-proxy-exceptions");
      String proxyExceptionsSeparator = ",";
      if (proxyExceptions == null) {
          proxyExceptions = System.getProperty("http.nonProxyHosts");
          proxyExceptionsSeparator = "|";
      }
      if (proxyExceptions != null) {
        for(StringTokenizer exceptions = new StringTokenizer(proxyExceptions, proxyExceptionsSeparator); exceptions.hasMoreTokens();) {
            String exception = exceptions.nextToken().trim();
            if (DefaultSVNOptions.matches(exception, host)) {
                return null;
            }
        }
      }
      String proxyPort = (String) properties.get("http-proxy-port");
      String proxyUser = (String) properties.get("http-proxy-username");
      String proxyPassword = (String) properties.get("http-proxy-password");
      return new MySimpleProxyManager(proxyHost, proxyPort, proxyUser, proxyPassword);
  }

  private static class MyPromptingProxyManager extends MySimpleProxyManager {
    private static final String ourPrompt = "Proxy authentication";

    private MyPromptingProxyManager(final String host, final String port) {
      super(host, port, null, null);
    }

    @Override
    public String getProxyUserName() {
      if (myProxyUser != null) {
        return myProxyUser;
      }
      final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
      if (httpConfigurable.PROXY_AUTHENTICATION && (! httpConfigurable.KEEP_PROXY_PASSWORD)) {
        httpConfigurable.getPromptedAuthentication(myProxyHost, ourPrompt);
      }
      myProxyUser = httpConfigurable.PROXY_LOGIN;
      return myProxyUser;
    }

    @Override
    public String getProxyPassword() {
      if (myProxyPassword != null) {
        return myProxyPassword;
      }
      final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
      if (httpConfigurable.PROXY_AUTHENTICATION && (! httpConfigurable.KEEP_PROXY_PASSWORD)) {
        httpConfigurable.getPromptedAuthentication(myProxyUser, ourPrompt);
      }
      myProxyPassword = httpConfigurable.getPlainProxyPassword();
      return myProxyPassword;
    }
  }

  private static class MySimpleProxyManager implements ISVNProxyManager {
      protected String myProxyHost;
      private final String myProxyPort;
      protected String myProxyUser;
      protected String myProxyPassword;

      public MySimpleProxyManager(String host, String port, String user, String password) {
          myProxyHost = host;
          myProxyPort = port == null ? "3128" : port;
          myProxyUser = user;
          myProxyPassword = password;
      }

      public String getProxyHost() {
          return myProxyHost;
      }

      public int getProxyPort() {
          try {
              return Integer.parseInt(myProxyPort);
          } catch (NumberFormatException nfe) {
              //
          }
          return 3128;
      }

      public String getProxyUserName() {
          return myProxyUser;
      }

      public String getProxyPassword() {
          return myProxyPassword;
      }

      public void acknowledgeProxyContext(boolean accepted, SVNErrorMessage errorMessage) {
      }
  }

  // 30 seconds
  private final static int DEFAULT_READ_TIMEOUT = 30 * 1000;

  @Override
  public int getReadTimeout(final SVNRepository repository) {
    String protocol = repository.getLocation().getProtocol();
    if ("http".equals(protocol) || "https".equals(protocol)) {
        String host = repository.getLocation().getHost();
        Map properties = getHostProperties(host);
        String timeout = (String) properties.get("http-timeout");
        if (timeout != null) {
            try {
                return Integer.parseInt(timeout)*1000;
            } catch (NumberFormatException nfe) {
              // use default
            }
        }
        return DEFAULT_READ_TIMEOUT;
    }
    return 0;
  }

  // taken from default manager as is
  private Map getHostProperties(String host) {
      Map globalProps = getServersFile().getProperties("global");
      String groupName = getGroupName(getServersFile().getProperties("groups"), host);
      if (groupName != null) {
          Map hostProps = getServersFile().getProperties(groupName);
          globalProps.putAll(hostProps);
      }
      return globalProps;
  }

  public static boolean checkHostGroup(final String url, final String patterns, final String exceptions) {
    final SVNURL svnurl;
    try {
      svnurl = SVNURL.parseURIEncoded(url);
    }
    catch (SVNException e) {
      return false;
    }
    
    final String host = svnurl.getHost();
    return matches(patterns, host) && (! matches(exceptions, host));
  }

  private static boolean matches(final String pattern, final String host) {
    final StringTokenizer tokenizer = new StringTokenizer(pattern, ",");
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (DefaultSVNOptions.matches(token, host)) {
          return true;
      }
    }
    return false;
  }

  // taken from default manager as is
  private static String getGroupName(Map groups, String host) {
      for (Iterator names = groups.keySet().iterator(); names.hasNext();) {
          String name = (String) names.next();
          String pattern = (String) groups.get(name);
          for(StringTokenizer tokens = new StringTokenizer(pattern, ","); tokens.hasMoreTokens();) {
              String token = tokens.nextToken();
              if (DefaultSVNOptions.matches(token, host)) {
                  return name;
              }
          }
      }
      return null;
  }

  private static class SvnAuthWrapperEqualable extends Wrapper<SVNAuthentication> {
    private SvnAuthWrapperEqualable(SVNAuthentication svnAuthentication) {
      super(svnAuthentication);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) return false;
      if (this == obj) return true;
      if (obj instanceof SvnAuthWrapperEqualable) {
        return SvnAuthEquals.equals(this.getT(), ((SvnAuthWrapperEqualable) obj).getT());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return SvnAuthEquals.hashCode(getT());
    }
  }
}
