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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.changes.committed.AbstractCalledLater;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.EventDispatcher;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.idea.svn.auth.ProviderType;
import org.jetbrains.idea.svn.auth.SvnAuthenticationInteraction;
import org.jetbrains.idea.svn.auth.SvnAuthenticationListener;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.internal.wc.*;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author alex
 */
public class SvnAuthenticationManager extends DefaultSVNAuthenticationManager implements SvnAuthenticationListener {
  // while Mac storage not working for IDEA, we use this key to check whether to prompt abt plaintext or just store
  private static final Logger LOG = Logger.getInstance(SvnAuthenticationManager.class.getName());
  private final Project myProject;
  private File myConfigDirectory;
  private PersistentAuthenticationProviderProxy myPersistentAuthenticationProviderProxy;
  private SvnConfiguration myConfig;
  private static final ThreadLocal<Boolean> ourJustEntered = new ThreadLocal<Boolean>();
  private SvnAuthenticationInteraction myInteraction;
  private EventDispatcher<SvnAuthenticationListener> myListener;
  private IdeaSVNHostOptionsProvider myLocalHostOptionsProvider;
  private final ThreadLocalSavePermissions mySavePermissions;

  public SvnAuthenticationManager(final Project project, final File configDirectory) {
    super(configDirectory, true, null, null);
    myProject = project;
    myConfigDirectory = configDirectory;
    ensureListenerCreated();
    mySavePermissions = new ThreadLocalSavePermissions();
    myConfig = SvnConfiguration.getInstance(myProject);
    if (myPersistentAuthenticationProviderProxy != null) {
      myPersistentAuthenticationProviderProxy.setProject(myProject);
    }
    myInteraction = new MySvnAuthenticationInteraction(myProject);
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
  public void requested(ProviderType type, SVNURL url, String realm, String kind, boolean canceled) {
    if (ProviderType.interactive.equals(type) && (! canceled)) {
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

  @Override
  public void acknowledgeAuthentication(boolean accepted,
                                        String kind,
                                        String realm,
                                        SVNErrorMessage errorMessage,
                                        SVNAuthentication authentication) throws SVNException {
    try {
      final boolean authStorageEnabled = getHostOptionsProvider().getHostOptions(authentication.getURL()).isAuthStorageEnabled();
      final SVNAuthentication proxy = ProxySvnAuthentication.proxy(authentication, authStorageEnabled);
      super.acknowledgeAuthentication(accepted, kind, realm, errorMessage, proxy);
    } finally {
      mySavePermissions.remove();
    }
  }

  private class PersistentAuthenticationProviderProxy implements ISVNAuthenticationProvider, ISVNPersistentAuthenticationProvider {
    private final ISVNAuthenticationProvider myDelegate;
    private final File myAuthDir;
    private Project myProject;

    private PersistentAuthenticationProviderProxy(File authDir, String userName) {
      ISVNAuthenticationStorageOptions delegatingOptions = new ISVNAuthenticationStorageOptions() {
            public boolean isNonInteractive() throws SVNException {
                return getAuthenticationStorageOptions().isNonInteractive();
            }

            public ISVNAuthStoreHandler getAuthStoreHandler() throws SVNException {
                return getAuthenticationStorageOptions().getAuthStoreHandler();
            }

        @Override
        public boolean isSSLPassphrasePromptSupported() {
          return false;
        }
      };
      ensureListenerCreated();
      myDelegate = new DefaultSVNPersistentAuthenticationProvider(authDir, userName, delegatingOptions, getDefaultOptions(), getHostOptionsProvider()) {
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

    public SVNAuthentication requestClientAuthentication(final String kind, final SVNURL url, final String realm, final SVNErrorMessage errorMessage,
                                                         final SVNAuthentication previousAuth, final boolean authMayBeStored) {
      final SVNAuthentication svnAuthentication =
        myDelegate.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
      myListener.getMulticaster().requested(ProviderType.persistent, url, realm, kind, svnAuthentication == null);
      return svnAuthentication;
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
            ((ISVNPersistentAuthenticationProvider) myDelegate).saveAuthentication(auth, actualKind, realm);
          }
          catch (SVNException e) {
            if (myProject != null) {
              ApplicationManager.getApplication().invokeLater(new VcsBalloonProblemNotifier(myProject,
                "<b>Problem when storing Subversion credentials:</b>&nbsp;" + e.getMessage(), MessageType.ERROR));
            }
          }
          finally {
            // do not make password file readonly
            setWriteable(authFile);
          }
    }

    public void saveAuthentication(final SVNAuthentication auth, final String kind, final String realm) throws SVNException {
      final Boolean fromInteractive = ourJustEntered.get();
      ourJustEntered.set(null);
      if (! Boolean.TRUE.equals(fromInteractive)) {
        // not what user entered
        return;
      }

      myListener.getMulticaster().saveAttemptStarted(ProviderType.persistent, auth.getURL(), realm, auth.getKind());
      ((ISVNPersistentAuthenticationProvider) myDelegate).saveAuthentication(auth, kind, realm);
      myListener.getMulticaster().saveAttemptFinished(ProviderType.persistent, auth.getURL(), realm, auth.getKind());
    }

    @Override
    public void saveFingerprints(String realm, byte[] fingerprints) {
      ((ISVNPersistentAuthenticationProvider) myDelegate).saveFingerprints(realm, fingerprints);
    }

    @Override
    public byte[] loadFingerprints(String realm) {
      return ((ISVNPersistentAuthenticationProvider) myDelegate).loadFingerprints(realm);
    }

    private final static int maxAttempts = 10;
    private void setWriteable(final File file) {
      if (! file.exists()) return;
      if (file.getParentFile() == null) {
        return;
      }
      for (int i = 0; i < maxAttempts; i++) {
        final File parent = file.getParentFile();
        try {
          final File tempFile = FileUtil.createTempFile(parent, "123", "1", true);
          FileUtil.delete(tempFile);
          if (! file.renameTo(tempFile)) continue;
          if (! file.createNewFile()) continue;
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

  public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
    // this code taken from default manager (changed for system properties reading)
      String host = url.getHost();

    String proxyHost = getServersPropertyIdea(host, "http-proxy-host");
    if ((proxyHost == null) || "".equals(proxyHost.trim())) {
      if (myConfig.isIsUseDefaultProxy()) {
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
      String proxyExceptions = getServersPropertyIdea(host, "http-proxy-exceptions");
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
      String proxyPort = getServersPropertyIdea(host, "http-proxy-port");
      String proxyUser = getServersPropertyIdea(host, "http-proxy-username");
      String proxyPassword = getServersPropertyIdea(host, "http-proxy-password");
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
        String timeout = getServersPropertyIdea(host, "http-timeout");
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
  private String getServersPropertyIdea(String host, final String name) {
    final SVNCompositeConfigFile serversFile = getHostOptionsProvider().getServersFile();
    return getPropertyIdea(host, serversFile, name);
  }

  private String getPropertyIdea(String host, SVNCompositeConfigFile serversFile, final String name) {
    String groupName = getGroupName(serversFile.getProperties("groups"), host);
    if (groupName != null) {
      Map hostProps = serversFile.getProperties(groupName);
      final String value = (String)hostProps.get(name);
      if (value != null) {
        return value;
      }
    }
    Map globalProps = serversFile.getProperties("global");
    return (String) globalProps.get(name);
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

  // default = yes
  private static boolean isTurned(final String value) {
    return value == null || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
  }

  private ModalityState getCurrent() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return ModalityState.current();
    }
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
    if (pi == null) {
      return ModalityState.defaultModalityState();
    }
    return pi.getModalityState();
  }

  /**
   * Shows a yes/no question whether user wants to store his password in plain text and returns his answer.
   * @param title   title of the questioning dialog.
   * @param message questioning message to be displayed.
   * @return true if user agrees to store his password in plaintext, false if he doesn't.
   */
  @CalledInAwt
  private boolean askToStoreUnencrypted(String title, String message) {
    final int answer = Messages.showYesNoDialog(myProject, message, title, Messages.getQuestionIcon());
    return answer == 0;
  }

  public void setInteraction(SvnAuthenticationInteraction interaction) {
    myInteraction = interaction;
  }

  private static class MySvnAuthenticationInteraction implements SvnAuthenticationInteraction {
    private final Project myProject;

    private MySvnAuthenticationInteraction(Project project) {
      myProject = project;
    }

    @Override
    public void warnOnAuthStorageDisabled(SVNURL url) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Cannot store credentials: forbidden by \"store-auth-creds=no\"", MessageType.ERROR);
    }

    @Override
    public void warnOnPasswordStorageDisabled(SVNURL url) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Cannot store password: forbidden by \"store-passwords=no\"", MessageType.ERROR);
    }

    @Override
    public void warnOnSSLPassphraseStorageDisabled(SVNURL url) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Cannot store passphrase: forbidden by \"store-ssl-client-cert-pp=no\"", MessageType.ERROR);
    }

    @Override
    public boolean promptForPlaintextPasswordSaving(SVNURL url, String realm) {
      final int answer = Messages.showYesNoDialog(myProject, String.format("Your password for authentication realm:\n" +
        "%s\ncan only be stored to disk unencrypted. Would you like to store it in plaintext?", realm),
        "Store the password in plaintext?", Messages.getQuestionIcon());
      return answer == 0;
    }

    @Override
    public boolean promptInAwt() {
      return true;
    }

    @Override
    public boolean promptForSSLPlaintextPassphraseSaving(SVNURL url, String realm, File certificateFile) {
      final int answer = Messages.showYesNoDialog(myProject, String.format("Your passphrase for client certificate:\n%s\ncan only be stored to disk unencrypted. Would you like to store it in plaintext?",
                                                            certificateFile.getPath()),
        "Store the passphrase in plaintext?", Messages.getQuestionIcon());
      return answer == 0;
    }
  }

  private static boolean isLion() {
    return SystemInfo.isMac && SystemInfo.isMacOSSnowLeopard && ! SystemInfo.OS_VERSION.startsWith("10.6");
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
      if ((! value) && (! mySavePermissions.have())) {
        promptAndSaveWhenWeLackEncryption(realm, auth, new Getter<Boolean>() {
                                                @Override
                                                public Boolean get() {
                                                  return myInteraction.promptForPlaintextPasswordSaving(myUrl, realm);
                                                }
                                              });
      }
      return value;
    }

    @Override
    public boolean isStorePlainTextPassphrases(final String realm, final SVNAuthentication auth) throws SVNException {
      if (USERNAME.equals(auth.getKind())) return true;

      final boolean value = mySavePermissions.allowed() || super.isStorePlainTextPassphrases(realm, auth);
      if ((! value) && (! mySavePermissions.have())) {
        promptAndSaveWhenWeLackEncryption(realm, auth, new Getter<Boolean>() {
                                                @Override
                                                public Boolean get() {
                                                  return myInteraction.promptForSSLPlaintextPassphraseSaving(myUrl, realm,
                                                           ((SVNSSLAuthentication) auth).getCertificateFile());
                                                }
                                              });
      }
      return value;
    }

    @Override
    public boolean isAuthStorageEnabled() {
      final boolean value;
      if (hasAuthStorageEnabledOption()) {
        value = super.isAuthStorageEnabled();
      } else {
        value = isTurned(getConfigFile().getPropertyValue("auth", "store-auth-creds"));
      }
      if (! value) {
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
      } else {
        final String configValue = getConfigFile().getPropertyValue("auth", "store-passwords");
        value = isTurned(configValue);
      }
      if (! value) {
        myInteraction.warnOnPasswordStorageDisabled(myUrl);
      }
      return value;
    }

    @Override
    public boolean isStoreSSLClientCertificatePassphrases() {
      final boolean value = super.isStoreSSLClientCertificatePassphrases();
      if (! value) {
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
      final Runnable actualSave = new Runnable() {
        @Override
        public void run() {
          mySavePermissions.put(Boolean.TRUE.equals(saveOnce[0]));
          try {
            myPersistentAuthenticationProviderProxy.actualSavePermissions(realm, auth);
          }
          finally {
            mySavePermissions.remove();
          }
        }
      };

      if (myInteraction.promptInAwt()) {
        new AbstractCalledLater(myProject, getCurrent()) {
          @Override
          public void run() {
            saveOnce[0] = Boolean.TRUE.equals(prompt.get());
            ApplicationManager.getApplication().executeOnPooledThread(actualSave);
          }
        }.callMe();
      } else {
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
    public boolean savePassword(String realm, String password, SVNAuthentication auth, SVNProperties authParameters) throws SVNException {
      final boolean saved = myDelegate.savePassword(realm, password, auth, authParameters);
      if (saved) {
        myListener.getMulticaster().actualSaveWillBeTried(ProviderType.persistent, auth.getURL(), realm, auth.getKind()
        );
      }
      return saved;
    }

    @Override
    public String readPassword(String realm, String userName, SVNProperties authParameters) throws SVNException {
      return myDelegate.readPassword(realm, userName, authParameters);
    }

    @Override
    public boolean savePassphrase(String realm, String passphrase, SVNAuthentication auth, SVNProperties authParameters, boolean force)
      throws SVNException {
      final boolean saved = myDelegate.savePassphrase(realm, passphrase, auth, authParameters, force);
      if (saved) {
        myListener.getMulticaster().actualSaveWillBeTried(ProviderType.persistent, auth.getURL(), realm, auth.getKind()
        );
      }
      return saved;
    }

    @Override
    public String readPassphrase(String realm, SVNProperties authParameters) throws SVNException {
      return myDelegate.readPassphrase(realm, authParameters);
    }
  }
}
