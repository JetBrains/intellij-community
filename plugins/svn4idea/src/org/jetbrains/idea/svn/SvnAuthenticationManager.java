package org.jetbrains.idea.svn;

import com.intellij.util.net.HttpConfigurable;
import com.intellij.openapi.project.Project;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: Sep 24, 2005
 * Time: 5:25:35 PM
 * To change this template use File | Settings | File Templates.
 */
public class SvnAuthenticationManager extends DefaultSVNAuthenticationManager {
  private final Project myProject;
    public SvnAuthenticationManager(final Project project, final File configDirectory) {
        super(configDirectory, true, null, null);
      myProject = project;
    }

    protected ISVNAuthenticationProvider createCacheAuthenticationProvider(File authDir) {
        return new ApplicationAuthenticationProvider();
    }

  // todo clarify whether it is OK that this method is not used, no more overrides "create Default..."
  protected ISVNAuthenticationProvider createDefaultAuthenticationProvider(String userName, String password, boolean allowSave) {
        return new ISVNAuthenticationProvider() {
            public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
                return null;
            }
            public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
                return ACCEPTED;
            }
        };
    }

  class ApplicationAuthenticationProvider implements ISVNAuthenticationProvider, IPersistentAuthenticationProvider {

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
          if (ISVNAuthenticationManager.SSL.equals(kind)) {
                  String host = url.getHost();
                  Map properties = getHostProperties(host);
                  String sslClientCert = (String) properties.get("ssl-client-cert-file"); // PKCS#12
                  String sslClientCertPassword = (String) properties.get("ssl-client-cert-password");
                  File clientCertFile = sslClientCert != null ? new File(sslClientCert) : null;
                  return new SVNSSLAuthentication(clientCertFile, sslClientCertPassword, authMayBeStored);
          }

          // get from key-ring, use realm.
            Map info = SvnApplicationSettings.getInstance().getAuthenticationInfo(realm, kind);
            // convert info to SVNAuthentication.
            if (info != null && !info.isEmpty() && info.get("username") != null) {
                if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    return new SVNPasswordAuthentication((String) info.get("username"), (String) info.get("password"), authMayBeStored);
                } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    int port = url.hasPort() ? url.getPort() : -1;
                    if (port < 0 && info.get("port") != null) {
                        port = Integer.parseInt((String) info.get("port"));
                    }
                    if (port < 0) {
                        port = 22;
                    }
                    if (info.get("key") != null) {
                        File keyPath = new File((String) info.get("key"));
                        return new SVNSSHAuthentication((String) info.get("username"), keyPath, (String) info.get("passphrase"), port, authMayBeStored);
                    } else if (info.get("password") != null) {
                        return new SVNSSHAuthentication((String) info.get("username"), (String) info.get("password"), port, authMayBeStored);
                    }
                } if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                    return new SVNUserNameAuthentication((String) info.get("username"), authMayBeStored);
                }
            }
            return null;
        }

        public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
            return ACCEPTED_TEMPORARY;
        }

        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) {
            if (auth.getUserName() == null || "".equals(auth.getUserName())) {
                return;
            }
            Map<String, String> info = new HashMap<String, String>();
            info.put("kind", kind);
            // convert info to SVNAuthentication.
            info.put("username", auth.getUserName());
            if (auth instanceof SVNPasswordAuthentication) {
                info.put("password", ((SVNPasswordAuthentication) auth).getPassword());
            } else if (auth instanceof SVNSSHAuthentication) {
                SVNSSHAuthentication sshAuth = (SVNSSHAuthentication) auth;
                if (sshAuth.getPrivateKeyFile() != null) {
                    info.put("key", sshAuth.getPrivateKeyFile().getAbsolutePath());
                    if (sshAuth.getPassphrase() != null) {
                        info.put("passphrase", sshAuth.getPassphrase());
                    }
                } else if (sshAuth.getPassword() != null) {
                    info.put("password", sshAuth.getPassword());
                }
                if (sshAuth.getPortNumber() >= 0) {
                    info.put("port", Integer.toString(sshAuth.getPortNumber()));
                }
            }
            SvnApplicationSettings.getInstance().saveAuthenticationInfo(realm, kind, info);
        }

    }

  public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
    // this code taken from default manager (changed for system properties reading)
      String host = url.getHost();

      Map properties = getHostProperties(host);
      String proxyHost = (String) properties.get("http-proxy-host");
    if ((proxyHost == null) || "".equals(proxyHost.trim())) {
      if (SvnConfiguration.getInstance(myProject).isIsUseDefaultProxy()) {
        // ! use common proxy if it is set
        final String ideaWideProxyHost = System.getProperty("http.proxyHost");
        String ideaWideProxyPort = System.getProperty("http.proxyPort");

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
      private String myProxyPort;
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
}
