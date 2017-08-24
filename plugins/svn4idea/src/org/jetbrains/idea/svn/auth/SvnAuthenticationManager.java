/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.Topic;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.ui.UIUtil;
import com.trilead.ssh2.auth.AgentProxy;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.config.ProxyGroup;
import org.jetbrains.idea.svn.config.SvnServerFileKeys;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.wc.*;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;

import static com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress;

/**
 * @author alex
 */
public class SvnAuthenticationManager extends DefaultSVNAuthenticationManager
  implements SvnAuthenticationListener, ISVNAuthenticationManagerExt {
  private static final Logger LOG = Logger.getInstance(SvnAuthenticationManager.class);
  // while Mac storage not working for IDEA, we use this key to check whether to prompt abt plaintext or just store
  public static final String SVN_SSH = "svn+ssh";
  public static final String HTTP = "http";
  public static final String HTTPS = "https";
  public static final String HTTP_PROXY_HOST = "http-proxy-host";
  public static final String HTTP_PROXY_PORT = "http-proxy-port";
  public static final String HTTP_PROXY_USERNAME = "http-proxy-username";
  public static final String HTTP_PROXY_PASSWORD = "http-proxy-password";
  private SvnVcs myVcs;
  private Project myProject;
  private File myConfigDirectory;
  private ISVNAuthenticationProvider myRuntimeCacheProvider;
  private PersistentAuthenticationProviderProxy myPersistentAuthenticationProviderProxy;
  private SvnConfiguration myConfig;
  private static final ThreadLocal<Boolean> ourJustEntered = new ThreadLocal<>();
  private SvnAuthenticationInteraction myInteraction;
  private EventDispatcher<SvnAuthenticationListener> myListener;
  private IdeaSVNHostOptionsProvider myLocalHostOptionsProvider;
  private final ThreadLocalSavePermissions mySavePermissions;
  private final Map<Thread, String> myKeyAlgorithm;
  private boolean myArtificialSaving;
  private ISVNAuthenticationProvider myProvider;
  public static final Topic<ISVNAuthenticationProviderListener> AUTHENTICATION_PROVIDER_LISTENER =
    new Topic<>("AUTHENTICATION_PROVIDER_LISTENER", ISVNAuthenticationProviderListener.class);
  private final static ThreadLocal<ISVNAuthenticationProvider> ourThreadLocalProvider = new ThreadLocal<>();

  public SvnAuthenticationManager(@NotNull SvnVcs vcs, final File configDirectory) {
    super(configDirectory, true, null, null);
    myVcs = vcs;
    myProject = myVcs.getProject();
    myConfigDirectory = configDirectory;
    myKeyAlgorithm = new HashMap<>();
    ensureListenerCreated();
    mySavePermissions = new ThreadLocalSavePermissions();
    myConfig = myVcs.getSvnConfiguration();
    if (myPersistentAuthenticationProviderProxy != null) {
      myPersistentAuthenticationProviderProxy.setProject(myProject);
    }
    myInteraction = new MySvnAuthenticationInteraction(myProject);
    Disposer.register(myProject, () -> {
      myVcs = null;
      myProject = null;
      if (myPersistentAuthenticationProviderProxy != null) {
        myPersistentAuthenticationProviderProxy.myProject = null;
        ((MyKeyringMasterKeyProvider)myPersistentAuthenticationProviderProxy.myISVNGnomeKeyringPasswordProvider).myProject = null;
        myPersistentAuthenticationProviderProxy = null;
      }
      if (myInteraction instanceof MySvnAuthenticationInteraction) {
        ((MySvnAuthenticationInteraction)myInteraction).myProject = null;
      }
      if (myConfig != null) {
        myConfig.clear();
        myConfig = null;
      }
      myInteraction = null;
    });
    // This is not the same instance as DefaultSVNAuthenticationManager.myProviders[1], but currently
    // DefaultSVNAuthenticationManager.CacheAuthenticationProvider uses only its outer class state - so we utilize necessary logic with
    // this new instance.
    myRuntimeCacheProvider = createRuntimeAuthenticationProvider();
  }

  public SVNAuthentication requestFromCache(String kind,
                                            SVNURL url,
                                            String realm,
                                            SVNErrorMessage errorMessage,
                                            SVNAuthentication previousAuth,
                                            boolean authMayBeStored) {
    return myRuntimeCacheProvider.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
  }

  public String getDefaultUsername(String kind, SVNURL url) {
    String result = SystemProperties.getUserName();

    // USERNAME authentication is also requested in SVNSSHConnector.open()
    if (ISVNAuthenticationManager.SSH.equals(kind) ||
        (ISVNAuthenticationManager.USERNAME.equals(kind) && SVN_SSH.equals(url.getProtocol()))) {
      result = url != null && !StringUtil.isEmpty(url.getUserInfo()) ? url.getUserInfo() : getDefaultOptions().getDefaultSSHUserName();
    }

    return result;
  }

  @Override
  protected SVNSSHAuthentication getDefaultSSHAuthentication(SVNURL url) {
    String userName = getDefaultUsername(ISVNAuthenticationManager.SSH, url);

    // This is fully copied from base class - DefaultSVNAuthenticationManager - as there are no setters in Authentication classes
    // and there is no url parameter if overriding getDefaultOptions()
    String password = getDefaultOptions().getDefaultSSHPassword();
    String keyFile = getDefaultOptions().getDefaultSSHKeyFile();
    int port = getDefaultOptions().getDefaultSSHPortNumber();
    String passphrase = getDefaultOptions().getDefaultSSHPassphrase();

    if (userName != null && password != null) {
      return new SVNSSHAuthentication(userName, password, port, getHostOptionsProvider().getHostOptions(url).isAuthStorageEnabled(), url,
                                      false);
    }
    else if (userName != null && keyFile != null) {
      return new SVNSSHAuthentication(userName, new File(keyFile), passphrase, port,
                                      getHostOptionsProvider().getHostOptions(url).isAuthStorageEnabled(), url, false);
    }
    return null;
  }

  private class AuthenticationProviderProxy implements ISVNAuthenticationProvider {
    private final ISVNAuthenticationProvider myDelegate;

    private AuthenticationProviderProxy(ISVNAuthenticationProvider delegate) {
      myDelegate = delegate;
    }

    @Override
    public SVNAuthentication requestClientAuthentication(String kind,
                                                         SVNURL url,
                                                         String realm,
                                                         SVNErrorMessage errorMessage,
                                                         SVNAuthentication previousAuth, boolean authMayBeStored) {
      final SVNAuthentication authentication =
        myDelegate.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
      BackgroundTaskUtil.syncPublisher(myProject, AUTHENTICATION_PROVIDER_LISTENER)
        .requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored, authentication);
      return authentication;
    }

    @Override
    public int acceptServerAuthentication(SVNURL url,
                                          String realm,
                                          Object certificate,
                                          boolean resultMayBeStored) {
      final int result = myDelegate.acceptServerAuthentication(url, realm, certificate, resultMayBeStored);
      BackgroundTaskUtil.syncPublisher(myProject, AUTHENTICATION_PROVIDER_LISTENER)
        .acceptServerAuthentication(url, realm, certificate, resultMayBeStored, result);
      return result;
    }
  }

  public interface ISVNAuthenticationProviderListener {
    void requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage,
                                     SVNAuthentication previousAuth, boolean authMayBeStored, SVNAuthentication authentication);

    void acceptServerAuthentication(SVNURL url,
                                    String realm,
                                    Object certificate,
                                    boolean resultMayBeStored,
                                    @MagicConstant int acceptResult);
  }

  @Override
  public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {
    ISVNAuthenticationProvider useProvider = provider;
    if (!(provider instanceof AuthenticationProviderProxy)) {
      useProvider = new AuthenticationProviderProxy(provider);
    }
    myProvider = useProvider;
    super.setAuthenticationProvider(myProvider);
  }

  public ISVNAuthenticationProvider getProvider() {
    final ISVNAuthenticationProvider threadProvider = ourThreadLocalProvider.get();
    if (threadProvider != null) return threadProvider;
    return myProvider;
  }

  /**
   * Gets authentication provider without looking into thread local storage for providers.
   * <p>
   * TODO:
   * Thread local storage is used "for some interaction with SVNKit" and is not always cleared correctly. So some threads contain
   * "passive provider" in thread local storage - and getProvider() returns this "passive provider". This occurs, for instance when
   * RemoteRevisionsCache is refreshed in background - after its execution, corresponding thread has "passive provider" in thread local
   * storage.
   * <p>
   * As a result authentication fails in such cases (at least for command line implementation). To fix this, command line implementation is
   * updated not to check thread local storage at all.
   *
   * @return
   */
  public ISVNAuthenticationProvider getInnerProvider() {
    return myProvider;
  }

  @Override
  public ISVNAuthenticationStorage getRuntimeAuthStorage() {
    return super.getRuntimeAuthStorage();
  }

  // since set to null during dispose and we have background processes
  private SvnConfiguration getConfig() {
    if (myConfig == null) throw new ProcessCanceledException();
    return myConfig;
  }

  public void setArtificialSaving(boolean artificialSaving) {
    myArtificialSaving = artificialSaving;
  }

  private void ensureListenerCreated() {
    if (myListener == null) {
      myListener = EventDispatcher.create(SvnAuthenticationListener.class);
    }
  }

  @Override
  public IdeaSVNHostOptionsProvider getHostOptionsProvider() {
    if (myLocalHostOptionsProvider == null) {
      myLocalHostOptionsProvider = new IdeaSVNHostOptionsProvider();
    }
    return myLocalHostOptionsProvider;
  }

  public void addListener(final SvnAuthenticationListener listener) {
    myListener.addListener(listener);
  }

  @Override
  public void actualSaveWillBeTried(ProviderType type, SVNURL url, String realm, String kind) {
    myListener.getMulticaster().actualSaveWillBeTried(type, url, realm, kind);
  }

  @Override
  public void saveAttemptStarted(ProviderType type, SVNURL url, String realm, String kind) {
    myListener.getMulticaster().saveAttemptStarted(type, url, realm, kind);
  }

  @Override
  public void saveAttemptFinished(ProviderType type, SVNURL url, String realm, String kind) {
    myListener.getMulticaster().saveAttemptFinished(type, url, realm, kind);
  }

  @Override
  public void acknowledge(boolean accepted, String kind, String realm, SVNErrorMessage message, SVNAuthentication authentication) {
    myListener.getMulticaster().acknowledge(accepted, kind, realm, message, authentication);
  }

  @Override
  public void requested(ProviderType type, SVNURL url, String realm, String kind, boolean canceled) {
    if (ProviderType.interactive.equals(type) && (!canceled)) {
      ourJustEntered.set(true);
    }
    myListener.getMulticaster().requested(type, url, realm, kind, canceled);
  }

  @Override
  protected ISVNAuthenticationProvider createCacheAuthenticationProvider(File authDir, String userName) {
    // this is a hack due to the fact this method is called from super() constructor
    myConfigDirectory = new File(authDir.getParent());
    myPersistentAuthenticationProviderProxy = new PersistentAuthenticationProviderProxy(authDir, userName);
    return myPersistentAuthenticationProviderProxy;
  }

  private class PersistentAuthenticationProviderProxy implements ISVNAuthenticationProvider, ISVNPersistentAuthenticationProvider {
    private final ISVNAuthenticationProvider myDelegate;
    private final ISVNGnomeKeyringPasswordProvider myISVNGnomeKeyringPasswordProvider;
    private final File myAuthDir;
    private Project myProject;

    private PersistentAuthenticationProviderProxy(File authDir, String userName) {
      myISVNGnomeKeyringPasswordProvider = new MyKeyringMasterKeyProvider(myProject);
      ISVNAuthenticationStorageOptions delegatingOptions = new ISVNAuthenticationStorageOptions() {
        public boolean isNonInteractive() throws SVNException {
          return getAuthenticationStorageOptions().isNonInteractive();
        }

        public ISVNAuthStoreHandler getAuthStoreHandler() throws SVNException {
          return getAuthenticationStorageOptions().getAuthStoreHandler();
        }

        @Override
        public ISVNGnomeKeyringPasswordProvider getGnomeKeyringPasswordProvider() {
          return myISVNGnomeKeyringPasswordProvider;
        }

        @Override
        public boolean isSSLPassphrasePromptSupported() {
          return false;
        }
      };
      ensureListenerCreated();
      myDelegate = new DefaultSVNPersistentAuthenticationProvider(authDir, userName, delegatingOptions, getDefaultOptions(),
                                                                  getHostOptionsProvider()) {
        @Override
        protected IPasswordStorage[] createPasswordStorages(DefaultSVNOptions options) {
          final IPasswordStorage[] passwordStorages = super.createPasswordStorages(options);
          final IPasswordStorage[] proxied = new IPasswordStorage[passwordStorages.length];
          for (int i = 0; i < passwordStorages.length; i++) {
            final IPasswordStorage storage = passwordStorages[i];
            proxied[i] = new ProxyPasswordStorageForDebug(storage, myListener);
          }
          return proxied;
        }
      };
      myAuthDir = authDir;
    }

    public void setProject(Project project) {
      myProject = project;
    }

    public SVNAuthentication requestClientAuthentication(final String kind,
                                                         final SVNURL url,
                                                         final String realm,
                                                         final SVNErrorMessage errorMessage,
                                                         final SVNAuthentication previousAuth,
                                                         final boolean authMayBeStored) {
      try {
        return wrapNativeCall(() -> {
          final SVNAuthentication svnAuthentication =
            myDelegate.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
          myListener.getMulticaster().requested(ProviderType.persistent, url, realm, kind, svnAuthentication == null);
          return svnAuthentication;
        });
      }
      catch (SVNException e) {
        LOG.info(e);
        throw new RuntimeException(e);
      }
    }

    public int acceptServerAuthentication(final SVNURL url, final String realm, final Object certificate, final boolean resultMayBeStored) {
      return ACCEPTED_TEMPORARY;
    }

    private void actualSavePermissions(String realm, SVNAuthentication auth) {
      final String actualKind = auth.getKind();
      File dir = new File(myAuthDir, actualKind);
      String fileName = SVNFileUtil.computeChecksum(realm);
      File authFile = new File(dir, fileName);

      try {
        ((ISVNPersistentAuthenticationProvider)myDelegate).saveAuthentication(auth, actualKind, realm);
      }
      catch (SVNException e) {
        if (myProject != null) {
          ApplicationManager.getApplication().invokeLater(new VcsBalloonProblemNotifier(myProject,
                                                                                        "<b>Problem when storing Subversion credentials:</b>&nbsp;" +
                                                                                        e.getMessage(), MessageType.ERROR));
        }
      }
      finally {
        // do not make password file readonly
        setWriteable(authFile);
      }
    }

    public void saveAuthentication(final SVNAuthentication auth, final String kind, final String realm) throws SVNException {
      try {
        wrapNativeCall(() -> {
          final Boolean fromInteractive = ourJustEntered.get();
          ourJustEntered.set(null);
          if (!myArtificialSaving && !Boolean.TRUE.equals(fromInteractive)) {
            // not what user entered
            return null;
          }
          myListener.getMulticaster().saveAttemptStarted(ProviderType.persistent, auth.getURL(), realm, auth.getKind());
          ((ISVNPersistentAuthenticationProvider)myDelegate).saveAuthentication(auth, kind, realm);
          myListener.getMulticaster().saveAttemptFinished(ProviderType.persistent, auth.getURL(), realm, auth.getKind());
          return null;
        });
      }
      catch (SVNException e) {
        LOG.info(e);
        throw new RuntimeException(e);
      }
    }

    @Override
    public void saveFingerprints(final String realm, final byte[] fingerprints) {
      try {
        wrapNativeCall(() -> {
          ((ISVNPersistentAuthenticationProvider)myDelegate).saveFingerprints(realm, fingerprints);
          return null;
        });
      }
      catch (SVNException e) {
        LOG.info(e);
        throw new RuntimeException(e);
      }
    }

    @Override
    public byte[] loadFingerprints(final String realm) {
      try {
        return wrapNativeCall(() -> ((ISVNPersistentAuthenticationProvider)myDelegate).loadFingerprints(realm));
      }
      catch (SVNException e) {
        LOG.info(e);
        throw new RuntimeException(e);
      }
    }

    private final static int maxAttempts = 10;

    private void setWriteable(final File file) {
      if (!file.exists()) return;
      if (file.getParentFile() == null) {
        return;
      }
      for (int i = 0; i < maxAttempts; i++) {
        final File parent = file.getParentFile();
        try {
          final File tempFile = FileUtil.createTempFile(parent, "123", "1", true);
          FileUtil.delete(tempFile);
          if (!file.renameTo(tempFile)) continue;
          if (!file.createNewFile()) continue;
          FileUtil.copy(tempFile, file);
          FileUtil.delete(tempFile);
          return;
        }
        catch (IOException e) {
          //
        }
      }
    }
  }

  @Override
  public void verifyHostKey(String hostName, int port, String keyAlgorithm, byte[] hostKey) throws SVNException {
    myKeyAlgorithm.put(Thread.currentThread(), keyAlgorithm);
    try {
      super.verifyHostKey(hostName, port, keyAlgorithm, hostKey);
    }
    finally {
      myKeyAlgorithm.remove(Thread.currentThread());
    }
  }

  @Nullable
  public String getSSHKeyAlgorithm() {
    return myKeyAlgorithm.get(Thread.currentThread());
  }

  @Override
  public void acknowledgeConnectionSuccessful(SVNURL url, String method) {
    CommonProxy.getInstance().removeNoProxy(url.getProtocol(), url.getHost(), url.getPort());
    SSLExceptionsHelper.removeInfo();
    ourThreadLocalProvider.remove();
  }

  @Override
  public void acknowledgeAuthentication(boolean accepted,
                                        String kind,
                                        String realm,
                                        SVNErrorMessage errorMessage,
                                        SVNAuthentication authentication) throws SVNException {
    acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication, null);
  }

  @Override
  public void acknowledgeAuthentication(boolean accepted,
                                        String kind,
                                        String realm,
                                        SVNErrorMessage errorMessage,
                                        SVNAuthentication authentication,
                                        SVNURL url) throws SVNException {
    showSshAgentErrorIfAny(errorMessage, authentication);

    SSLExceptionsHelper.removeInfo();
    ourThreadLocalProvider.remove();
    if (url != null) {
      CommonProxy.getInstance().removeNoProxy(url.getProtocol(), url.getHost(), url.getPort());
    }
    boolean successSaving = false;
    myListener.getMulticaster().acknowledge(accepted, kind, realm, errorMessage, authentication);
    try {
      final boolean authStorageEnabled = getHostOptionsProvider().getHostOptions(authentication.getURL()).isAuthStorageEnabled();
      final SVNAuthentication proxy = ProxySvnAuthentication.proxy(authentication, authStorageEnabled, myArtificialSaving);
      super.acknowledgeAuthentication(accepted, kind, realm, errorMessage, proxy);
      successSaving = true;
    }
    finally {
      mySavePermissions.remove();
      if (myArtificialSaving) {
        myArtificialSaving = false;
        throw new CredentialsSavedException(successSaving);
      }
    }
  }

  /**
   * "Pageant is not running" error thrown in PageantConnector.query() method is caught and "eaten" in SVNKit logic.
   * So for both cases "Pageant is not running" and "There are no valid keys in agent (both no keys at all and no valid keys for host)"
   * we will get same "Credentials rejected by SSH server" error.
   */
  private void showSshAgentErrorIfAny(@Nullable SVNErrorMessage errorMessage, @Nullable SVNAuthentication authentication) {
    if (errorMessage != null && authentication instanceof SVNSSHAuthentication) {
      AgentProxy agentProxy = ((SVNSSHAuthentication)authentication).getAgentProxy();

      if (agentProxy != null) {
        // TODO: Most likely this should be updated with new VcsNotifier api.
        VcsBalloonProblemNotifier.showOverChangesView(myProject, errorMessage.getFullMessage(), MessageType.ERROR);
      }
    }
  }

  public void acknowledgeForSSL(boolean accepted, SVNAuthentication proxy) {
    if (accepted && proxy instanceof SVNSSLAuthentication && (((SVNSSLAuthentication)proxy).getCertificateFile() != null)) {
      final SVNSSLAuthentication svnsslAuthentication = (SVNSSLAuthentication)proxy;
      final SVNURL url = svnsslAuthentication.getURL();

      final IdeaSVNHostOptionsProvider provider = getHostOptionsProvider();
      final SVNCompositeConfigFile serversFile = provider.getServersFile();
      String groupName = getGroupName(serversFile.getProperties("groups"), url.getHost());

      groupName = StringUtil.isEmptyOrSpaces(groupName) ? "global" : groupName;
      serversFile
        .setPropertyValue(groupName, SvnServerFileKeys.SSL_CLIENT_CERT_FILE, svnsslAuthentication.getCertificateFile().getPath(), true);
      serversFile.save();
    }
  }

  public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
    SSLExceptionsHelper.addInfo("Accessing URL: " + url.toString());
    ourThreadLocalProvider.set(myProvider);
    // in proxy creation, we need proxy information from common proxy. but then we should forbid common proxy to intercept
    final ISVNProxyManager proxy = createProxy(url);
    CommonProxy.getInstance().noProxy(url.getProtocol(), url.getHost(), url.getPort());
    return proxy;
  }

  private ISVNProxyManager createProxy(SVNURL url) {
    // this code taken from default manager (changed for system properties reading)
    String host = url.getHost();

    String proxyHost = getServersPropertyIdea(host, HTTP_PROXY_HOST);
    if (StringUtil.isEmptyOrSpaces(proxyHost)) {
      if (getConfig().isIsUseDefaultProxy()) {
        // ! use common proxy if it is set
        try {
          final List<Proxy> proxies = HttpConfigurable.getInstance().getOnlyBySettingsSelector().select(new URI(url.toString()));
          if (proxies != null && !proxies.isEmpty()) {
            for (Proxy proxy : proxies) {
              if (HttpConfigurable.isRealProxy(proxy) && Proxy.Type.HTTP.equals(proxy.type())) {
                final SocketAddress address = proxy.address();
                if (address instanceof InetSocketAddress) {
                  return new MyPromptingProxyManager(((InetSocketAddress)address).getHostName(),
                                                     String.valueOf(((InetSocketAddress)address).getPort()), url.getProtocol());
                }
              }
            }
          }
        }
        catch (URISyntaxException e) {
          LOG.info(e);
        }
      }
      return null;
    }
    String proxyExceptions = getServersPropertyIdea(host, "http-proxy-exceptions");
    String proxyExceptionsSeparator = ",";
    if (proxyExceptions == null) {
      proxyExceptions = System.getProperty("http.nonProxyHosts");
      proxyExceptionsSeparator = "|";
    }
    if (proxyExceptions != null) {
      for (StringTokenizer exceptions = new StringTokenizer(proxyExceptions, proxyExceptionsSeparator); exceptions.hasMoreTokens(); ) {
        String exception = exceptions.nextToken().trim();
        if (DefaultSVNOptions.matches(exception, host)) {
          return null;
        }
      }
    }
    String proxyPort = getServersPropertyIdea(host, HTTP_PROXY_PORT);
    String proxyUser = getServersPropertyIdea(host, HTTP_PROXY_USERNAME);
    String proxyPassword = getServersPropertyIdea(host, HTTP_PROXY_PASSWORD);
    return new MySimpleProxyManager(proxyHost, proxyPort, proxyUser, proxyPassword);
  }


  private static class MyPromptingProxyManager extends MySimpleProxyManager {

    private final String myProtocol;

    private MyPromptingProxyManager(final String host, final String port, String protocol) {
      super(host, port, null, null);
      myProtocol = protocol;
    }

    @Override
    public String getProxyUserName() {
      if (myProxyUser != null) {
        return myProxyUser;
      }
      tryGetCredentials();
      return myProxyUser;
    }

    private void tryGetCredentials() {
      try {
        final InetAddress ia = InetAddress.getByName(getProxyHost());
        final PasswordAuthentication authentication =
          Authenticator.requestPasswordAuthentication(getProxyHost(), ia, getProxyPort(), myProtocol, getProxyHost(), myProtocol,
                                                      null, Authenticator.RequestorType.PROXY);
        if (authentication != null) {
          myProxyUser = authentication.getUserName();
          myProxyPassword = String.valueOf(authentication.getPassword());
        }
      }
      catch (UnknownHostException e) {
        //
      }
    }

    @Override
    public String getProxyPassword() {
      if (myProxyPassword != null) {
        return myProxyPassword;
      }
      tryGetCredentials();
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
      }
      catch (NumberFormatException nfe) {
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

  @Override
  public int getReadTimeout(@NotNull SVNRepository repository) {
    return getReadTimeout(repository.getLocation());
  }

  @Override
  public int getConnectTimeout(@NotNull SVNRepository repository) {
    return getConnectTimeout(repository.getLocation());
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
      svnurl = SVNURL.parseURIEncoded(url);
    }
    catch (SVNException e) {
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

  private static ModalityState getCurrent() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return ModalityState.current();
    }
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
    if (pi == null) {
      return ModalityState.defaultModalityState();
    }
    return pi.getModalityState();
  }

  public void setInteraction(SvnAuthenticationInteraction interaction) {
    myInteraction = interaction;
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

  private static class ThreadLocalSavePermissions {
    private final Map<Thread, Boolean> myPlainTextAllowed;

    private ThreadLocalSavePermissions() {
      myPlainTextAllowed = Collections.synchronizedMap(new HashMap<Thread, Boolean>());
    }

    public void put(final boolean value) {
      myPlainTextAllowed.put(Thread.currentThread(), value);
    }

    public boolean have() {
      return myPlainTextAllowed.containsKey(Thread.currentThread());
    }

    public void remove() {
      myPlainTextAllowed.remove(Thread.currentThread());
    }

    public boolean allowed() {
      return Boolean.TRUE.equals(myPlainTextAllowed.get(Thread.currentThread()));
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
      if (USERNAME.equals(auth.getKind())) return true;

      final boolean superValue = super.isStorePlainTextPasswords(realm, auth);
      final boolean value = mySavePermissions.allowed() || superValue;
      if ((!value) && (!mySavePermissions.have())) {
        promptAndSaveWhenWeLackEncryption(realm, auth, () -> myInteraction.promptForPlaintextPasswordSaving(myUrl, realm));
      }
      return value;
    }

    @Override
    public boolean isStorePlainTextPassphrases(final String realm, final SVNAuthentication auth) throws SVNException {
      if (USERNAME.equals(auth.getKind())) return true;

      final boolean value = mySavePermissions.allowed() || super.isStorePlainTextPassphrases(realm, auth);
      if ((!value) && (!mySavePermissions.have())) {
        promptAndSaveWhenWeLackEncryption(realm, auth, () -> {
          File file = null;
          String certificateName = null;
          if (auth instanceof SVNSSLAuthentication) {
            file = ((SVNSSLAuthentication)auth).getCertificateFile();
            certificateName = "client certificate";
          }
          else if (auth instanceof SVNSSHAuthentication) {
            file = ((SVNSSHAuthentication)auth).getPrivateKeyFile();
            certificateName = "private key file";
          }
          else {
            assert false;
          }
          return myInteraction.promptForSSLPlaintextPassphraseSaving(myUrl, realm,
                                                                     file, certificateName);
        });
      }
      return value;
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
      final String storePasswords = getStorePasswords();
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

    public String getStorePasswords() {
      return getServersPropertyIdea(getHost(), "store-passwords");
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

    private void promptAndSaveWhenWeLackEncryption(final String realm, final SVNAuthentication auth, final Getter<Boolean> prompt) {
      final Boolean[] saveOnce = new Boolean[1];
      final Runnable actualSave = () -> {
        mySavePermissions.put(Boolean.TRUE.equals(saveOnce[0]));
        try {
          myPersistentAuthenticationProviderProxy.actualSavePermissions(realm, auth);
        }
        finally {
          mySavePermissions.remove();
        }
      };

      if (myInteraction.promptInAwt()) {
        runOrInvokeLaterAboveProgress(() -> {
          saveOnce[0] = Boolean.TRUE.equals(prompt.get());
          ApplicationManager.getApplication().executeOnPooledThread(actualSave);
        }, getCurrent(), myProject);
      }
      else {
        saveOnce[0] = Boolean.TRUE.equals(prompt.get());
        actualSave.run();
      }
    }
  }

  private static class ProxyPasswordStorageForDebug implements DefaultSVNPersistentAuthenticationProvider.IPasswordStorage {
    private final DefaultSVNPersistentAuthenticationProvider.IPasswordStorage myDelegate;
    private final EventDispatcher<SvnAuthenticationListener> myListener;

    public ProxyPasswordStorageForDebug(final DefaultSVNPersistentAuthenticationProvider.IPasswordStorage delegate,
                                        final EventDispatcher<SvnAuthenticationListener> listener) {
      myDelegate = delegate;
      myListener = listener;
    }

    @Override
    public String getPassType() {
      return myDelegate.getPassType();
    }

    @Override
    public boolean savePassword(String realm, char[] password, SVNAuthentication auth, SVNProperties authParameters) throws SVNException {
      final boolean saved = myDelegate.savePassword(realm, password, auth, authParameters);
      if (saved) {
        myListener.getMulticaster().actualSaveWillBeTried(ProviderType.persistent, auth.getURL(), realm, auth.getKind()
        );
      }
      return saved;
    }

    @Override
    public char[] readPassword(String realm, String userName, SVNProperties authParameters) throws SVNException {
      return myDelegate.readPassword(realm, userName, authParameters);
    }

    @Override
    public boolean savePassphrase(String realm, char[] passphrase, SVNAuthentication auth, SVNProperties authParameters, boolean force)
      throws SVNException {
      final boolean saved = myDelegate.savePassphrase(realm, passphrase, auth, authParameters, force);
      if (saved) {
        myListener.getMulticaster().actualSaveWillBeTried(ProviderType.persistent, auth.getURL(), realm, auth.getKind()
        );
      }
      return saved;
    }

    @Override
    public char[] readPassphrase(String realm, SVNProperties authParameters) throws SVNException {
      return myDelegate.readPassphrase(realm, authParameters);
    }
  }

  private <T> T wrapNativeCall(final ThrowableComputable<T, SVNException> runnable) throws SVNException {
    try {
      NativeLogReader.startTracking();
      final T t = runnable.compute();
      final List<NativeLogReader.CallInfo> logged = NativeLogReader.getLogged();
      final StringBuilder sb = new StringBuilder();
      for (NativeLogReader.CallInfo info : logged) {
        final String message = SvnNativeCallsTranslator.getMessage(info);
        if (message != null) {
          if (sb.length() > 0) sb.append('\n');
          sb.append(message);
        }
      }
      if (sb.length() > 0) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, sb.toString(), MessageType.ERROR);
        LOG.info(sb.toString());
      }
      return t;
    }
    finally {
      NativeLogReader.clear();
      NativeLogReader.endTracking();
    }
  }

  private static class MyKeyringMasterKeyProvider implements ISVNGnomeKeyringPasswordProvider {
    private Project myProject;

    public MyKeyringMasterKeyProvider(Project project) {
      myProject = project;
    }

    @Override
    public char[] getKeyringPassword(final String keyringName) throws SVNException {
      final String message = keyringName != null ? SvnBundle.message("gnome.keyring.prompt.named", keyringName)
                                                 : SvnBundle.message("gnome.keyring.prompt.nameless");
      final Ref<String> result = Ref.create();
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> result
        .set(Messages.showPasswordDialog(myProject, message, SvnBundle.message("subversion.name"), Messages.getQuestionIcon())));
      return !result.isNull() ? result.get().toCharArray() : null;
    }
  }

  public static class CredentialsSavedException extends RuntimeException {
    private final boolean mySuccess;

    public CredentialsSavedException(boolean success) {
      mySuccess = success;
    }

    public boolean isSuccess() {
      return mySuccess;
    }
  }
}
