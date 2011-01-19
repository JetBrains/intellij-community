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
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.idea.svn.auth.ProviderType;
import org.jetbrains.idea.svn.auth.SvnAuthenticationInteraction;
import org.jetbrains.idea.svn.auth.SvnAuthenticationListener;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.util.jna.SVNJNAUtil;
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
  private static final String ourMacPlaintextNicht = "svn.ourMacPlaintextNicht";
  private static final Logger LOG = Logger.getInstance(SvnAuthenticationManager.class.getName());
  private final Project myProject;
  private File myConfigDirectory;
  private PersistentAuthenticationProviderProxy myPersistentAuthenticationProviderProxy;
  private SvnConfiguration myConfig;
  private static final ThreadLocal<Boolean> ourJustEntered = new ThreadLocal<Boolean>();
  private SvnAuthenticationInteraction myInteraction;
  private final EventDispatcher<SvnAuthenticationListener> myListener;
  private IdeaSVNHostOptionsProvider myLocalHostOptionsProvider;
  private ThreadLocalSavePermissions mySavePermissions;
  private boolean myMacCryptIsOk;

  public SvnAuthenticationManager(final Project project, final File configDirectory) {
    super(configDirectory, true, null, null);
    myProject = project;
    myConfigDirectory = configDirectory;
    myMacCryptIsOk = Boolean.getBoolean(ourMacPlaintextNicht);
    myListener = EventDispatcher.create(SvnAuthenticationListener.class);
    myConfig = SvnConfiguration.getInstance(myProject);
    if (myPersistentAuthenticationProviderProxy != null) {
      myPersistentAuthenticationProviderProxy.setProject(myProject);
    }
    myInteraction = new MySvnAuthenticationInteraction(myProject);
  }

  @Override
  public IdeaSVNHostOptionsProvider getHostOptionsProvider() {
    if (myLocalHostOptionsProvider == null) {
      mySavePermissions = new ThreadLocalSavePermissions();
      myLocalHostOptionsProvider = new IdeaSVNHostOptionsProvider(mySavePermissions);
    }
    return myLocalHostOptionsProvider;
  }

  public void addListener(final SvnAuthenticationListener listener) {
    myListener.addListener(listener);
  }

  @Override
  public void actualSaveWillBeTried(ProviderType type, SVNURL url, String realm, String kind, boolean withCredentials) {
    myListener.getMulticaster().actualSaveWillBeTried(type, url, realm, kind, withCredentials);
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
    myPersistentAuthenticationProviderProxy = new PersistentAuthenticationProviderProxy(
      super.createCacheAuthenticationProvider(authDir, userName), authDir);
    return myPersistentAuthenticationProviderProxy;
  }

  @Override
  public void acknowledgeAuthentication(boolean accepted,
                                        String kind,
                                        String realm,
                                        SVNErrorMessage errorMessage,
                                        SVNAuthentication authentication) throws SVNException {
    try {
      super.acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication);
    } finally {
      mySavePermissions.remove();
    }
  }

  private class PersistentAuthenticationProviderProxy implements ISVNAuthenticationProvider, ISVNPersistentAuthenticationProvider {
    private final ISVNAuthenticationProvider myDelegate;
    private final File myAuthDir;
    private Project myProject;

    private PersistentAuthenticationProviderProxy(final ISVNAuthenticationProvider delegate, final File authDir) {
      myDelegate = delegate;
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

    public void saveAuthentication(final SVNAuthentication auth, final String kind, final String realm) throws SVNException {
      final Boolean fromInteractive = ourJustEntered.get();
      ourJustEntered.set(null);
      if (! Boolean.TRUE.equals(fromInteractive)) {
        // not what user entered
        return;
      }

      final String actualKind = auth.getKind();
      final Consumer<Boolean> actualSave = new Consumer<Boolean>() {
        @Override
        public void consume(final Boolean withCredentials) {
          File dir = new File(myAuthDir, actualKind);
          String fileName = SVNFileUtil.computeChecksum(realm);
          File authFile = new File(dir, fileName);

          myListener.getMulticaster().actualSaveWillBeTried(ProviderType.persistent, auth.getURL(), realm, actualKind,
                                                            Boolean.TRUE.equals(withCredentials));
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
      };
      if (USERNAME.equals(actualKind)) {
        actualSave.consume(true);
        return;
      }
      if (!auth.isStorageAllowed()) return;
      saveCredentialsIfAllowed(auth, actualKind, realm, actualSave);
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

  public void saveCredentialsIfAllowed(final SVNAuthentication auth, final String kind, final String realm,
                                                    final Consumer<Boolean> saveRunnable) {
    final SVNURL url = auth.getURL();
    final IdeaSVNHostOptionsProvider hostOptionsProvider = getHostOptionsProvider();
    final ISVNHostOptions hostOptions = hostOptionsProvider.getHostOptions(url);
    if (! hostOptions.isAuthStorageEnabled()) {
      myInteraction.warnOnAuthStorageDisabled(url);
      return;
    }
    boolean passwordWillBeSaved = true;
    // check can store
    if ((! ISVNAuthenticationManager.SSL.equals(kind)) && (! hostOptions.isStorePasswords())) {
      // but it should be
      myInteraction.warnOnPasswordStorageDisabled(url);
      passwordWillBeSaved = false;
    }
    if (ISVNAuthenticationManager.SSL.equals(kind) && (! hostOptionsProvider.getHostOptions(url).isStoreSSLClientCertificatePassphrases())) {
      myInteraction.warnOnSSLPassphraseStorageDisabled(url);
      passwordWillBeSaved = false;
    }

    // check can encrypt
    if (passwordWillBeSaved && (! ((SystemInfo.isWindows && SVNJNAUtil.isWinCryptEnabled()) ||
      (SystemInfo.isMacOSLeopard || SystemInfo.isMacOSSnowLeopard || isLion()) && SVNJNAUtil.isMacOsKeychainEnabled() && myMacCryptIsOk))) {
      try {
        if (ISVNAuthenticationManager.SSL.equals(kind)) {
          if (! hostOptionsProvider.getHostOptions(auth.getURL()).isStorePlainTextPassphrases(realm, auth)) {
            promptAndSaveWhenWeLackEncryption(saveRunnable,
                                              new Getter<Boolean>() {
                                                @Override
                                                public Boolean get() {
                                                  return myInteraction.promptForSSLPlaintextPassphraseSaving(url, realm, ((SVNSSLAuthentication)auth).getCertificateFile());
                                                }
                                              });
            return;
          }
        } else {
          if (! hostOptionsProvider.getHostOptions(auth.getURL()).isStorePlainTextPasswords(realm, auth)) {
            promptAndSaveWhenWeLackEncryption(saveRunnable,
                                              new Getter<Boolean>() {
                                                @Override
                                                public Boolean get() {
                                                  return myInteraction.promptForPlaintextPasswordSaving(url, realm);
                                                }
                                              });
            return;
          }
        }
      } catch (SVNException e) {
        LOG.info(e);
        return;
      }
    }
    saveRunnable.consume(passwordWillBeSaved);
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

  private void promptAndSaveWhenWeLackEncryption(
    final Consumer<Boolean> saveRunnable,
    final Getter<Boolean> prompt) {

    final Boolean[] saveOnce = new Boolean[1];
    final Runnable actualSave = new Runnable() {
      @Override
      public void run() {
        mySavePermissions.put(Boolean.TRUE.equals(saveOnce[0]));
        try {
          saveRunnable.consume(saveOnce[0]);
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
    private final ThreadLocalSavePermissions mySavePermissions;

    public IdeaSVNHostOptionsProvider(ThreadLocalSavePermissions savePermissions) {
      super(myConfigDirectory);
      mySavePermissions = savePermissions;
    }

    @Override
    public SVNCompositeConfigFile getServersFile() {
      return super.getServersFile();
    }

    @Override
    public ISVNHostOptions getHostOptions(SVNURL url) {
      return new IdeaSVNHostOptions(getServersFile(), url, mySavePermissions);
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

    public void remove() {
      myPlainTextAllowed.remove(Thread.currentThread());
    }

    public boolean allowed() {
      return Boolean.TRUE.equals(myPlainTextAllowed.get(Thread.currentThread()));
    }
  }

  private class IdeaSVNHostOptions extends DefaultSVNHostOptions {
    private final ThreadLocalSavePermissions mySavePermissions;
    private SVNCompositeConfigFile myConfigFile;

    private IdeaSVNHostOptions(SVNCompositeConfigFile serversFile,
                               SVNURL url,
                               ThreadLocalSavePermissions savePermissions) {
      super(serversFile, url);
      mySavePermissions = savePermissions;
    }

    public void put(final boolean value) {
      mySavePermissions.put(value);
    }

    public void remove() {
      mySavePermissions.remove();
    }

    @Override
    public boolean isStorePlainTextPasswords(String realm, SVNAuthentication auth) throws SVNException {
      return mySavePermissions.allowed() || super.isStorePlainTextPasswords(realm, auth);
    }

    @Override
    public boolean isStorePlainTextPassphrases(String realm, SVNAuthentication auth) throws SVNException {
      return mySavePermissions.allowed() || super.isStorePlainTextPassphrases(realm, auth);
    }

    @Override
    public boolean isAuthStorageEnabled() {
      if (hasAuthStorageEnabledOption()) {
        return super.isAuthStorageEnabled();
      }
      return isTurned(getConfigFile().getPropertyValue("auth", "store-auth-creds"));
    }

    @Override
    public boolean isStorePasswords() {
      final String storePasswords = getStorePasswords();
      if (storePasswords != null) {
        return isTurned(storePasswords);
      }
      final String configValue = getConfigFile().getPropertyValue("auth", "store-passwords");
      return isTurned(configValue);
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
  }
}
