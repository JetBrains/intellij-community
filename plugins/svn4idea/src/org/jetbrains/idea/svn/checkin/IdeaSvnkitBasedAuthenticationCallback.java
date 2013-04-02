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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCProperties;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/26/13
 * Time: 1:27 PM
 */
public class IdeaSvnkitBasedAuthenticationCallback implements AuthenticationCallback {
  private final SvnVcs myVcs;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.checkin.IdeaSvnkitBasedAuthenticationCallback");
  private File myTempDirectory;
  private boolean myProxyCredentialsWereReturned;
  private SvnConfiguration myConfiguration;

  public IdeaSvnkitBasedAuthenticationCallback(SvnVcs vcs) {
    myVcs = vcs;
    myConfiguration = SvnConfiguration.getInstance(myVcs.getProject());
  }

  @Override
  public boolean authenticateFor(String realm, File file, boolean previousFailed, boolean passwordRequest) {
    final File base = getExistingParent(file);
    if (base == null) return false;
    final SVNURL url = SvnUtil.getCommittedURL(myVcs, base);
    if (url == null) return false;

    return new CredentialsAuthenticator(myVcs).tryAuthenticate(realm, url, file, previousFailed, passwordRequest);
  }

  @Override
  public boolean acceptSSLServerCertificate(final File file, final String realm) {
    final File base = getExistingParent(file);
    if (base == null) return false;
    final SVNURL url = SvnUtil.getCommittedURL(myVcs, base);
    if (url == null) return false;

    return new SSLServerCertificateAuthenticator(myVcs).tryAuthenticate(url, realm);
  }

  @Override
  public void clearPassiveCredentials(String realm, File file, boolean password) {
    final File base = getExistingParent(file);
    if (base == null) return;
    final SVNURL url = SvnUtil.getCommittedURL(myVcs, base);
    if (url == null) return;
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
    final List<String> kinds = getKinds(url, password);

    for (String kind : kinds) {
      configuration.clearCredentials(kind, realm);
    }
  }

  @Override
  public boolean haveDataForTmpConfig() {
    final HttpConfigurable instance = HttpConfigurable.getInstance();
    return SvnConfiguration.getInstance(myVcs.getProject()).isIsUseDefaultProxy() &&
           (instance.USE_HTTP_PROXY || instance.USE_PROXY_PAC);
  }

  @Override
  public boolean persistDataToTmpConfig(final File baseFile) throws IOException, URISyntaxException {
    final File base = getExistingParent(baseFile);
    if (base == null) return false;
    final SVNURL url = SvnUtil.getCommittedURL(myVcs, base);
    if (url == null) return false;

    final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
    initTmpDir(configuration);

    final Proxy proxy = getIdeaDefinedProxy(url);
    if (proxy != null){
      SvnConfiguration.putProxyIntoServersFile(myTempDirectory, url.getHost(), proxy);
    }
    return true;
  }

  @Nullable
  private static Proxy getIdeaDefinedProxy(final SVNURL url) throws URISyntaxException {
    final List<Proxy> proxies = CommonProxy.getInstance().select(new URI(url.toString()));
    if (proxies != null && ! proxies.isEmpty()) {
      for (Proxy proxy : proxies) {
        if (HttpConfigurable.isRealProxy(proxy) && Proxy.Type.HTTP.equals(proxy.type())) {
          return proxy;
        }
      }
    }
    return null;
  }

  @Override
  public boolean askProxyCredentials(File baseFile) {
    final File base = getExistingParent(baseFile);
    if (base == null) return false;
    final SVNURL url = SvnUtil.getCommittedURL(myVcs, base);
    if (url == null) return false;

    final Proxy proxy;
    try {
      proxy = getIdeaDefinedProxy(url);
    }
    catch (URISyntaxException e) {
      LOG.info(e);
      return false;
    }
    if (proxy == null) return false;
    if (myProxyCredentialsWereReturned){
      // ask loud
      final HttpConfigurable instance = HttpConfigurable.getInstance();
      if (instance.USE_HTTP_PROXY || instance.USE_PROXY_PAC) {
        PopupUtil.showBalloonForActiveComponent("Failed to authenticate to proxy. You can change proxy credentials in HTTP proxy settings.", MessageType.ERROR);
      } else {
        PopupUtil.showBalloonForActiveComponent("Failed to authenticate to proxy.", MessageType.ERROR);
      }
      return false;
    }
    final InetSocketAddress address = (InetSocketAddress)proxy.address();
    final PasswordAuthentication authentication;
    try {
      authentication = Authenticator.requestPasswordAuthentication(url.getHost(), address.getAddress(),
                                                                   url.getPort(), url.getProtocol(), url.getHost(), url.getProtocol(),
                                                                   new URL(url.toString()), Authenticator.RequestorType.PROXY);
    } catch (MalformedURLException e) {
      LOG.info(e);
      return false;
    }
    if (authentication != null) {
      myProxyCredentialsWereReturned = true;
      // for 'generic' proxy variant (suppose user defined proxy in Subversion config but no password)
      try {
        initTmpDir(SvnConfiguration.getInstance(myVcs.getProject()));
      }
      catch (IOException e) {
        PopupUtil.showBalloonForActiveComponent("Failed to authenticate to proxy: " + e.getMessage(), MessageType.ERROR);
        return false;
      }
      return SvnConfiguration.putProxyCredentialsIntoServerFile(myTempDirectory, url.getHost(), authentication);
    }
    return false;
  }

  @Override
  public boolean acceptSSLServerCertificate(final String url, final String realm) {
    try {
      return new SSLServerCertificateAuthenticator(myVcs).tryAuthenticate(SVNURL.parseURIEncoded(url), realm);
    }
    catch (SVNException e) {
      return false;
    }
  }

  public void reset() {
    if (myTempDirectory != null) {
      FileUtil.delete(myTempDirectory);
    }
  }

  private abstract class AbstractAuthenticator<T> {
    protected final SvnVcs myVcs;
    protected boolean myStoreInUsual;
    protected SvnAuthenticationManager myTmpDirManager;

    protected AbstractAuthenticator(SvnVcs vcs) {
      myVcs = vcs;
    }

    protected boolean tryAuthenticate() {
      final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
      final SvnAuthenticationManager passive = configuration.getPassiveAuthenticationManager(myVcs.getProject());
      final SvnAuthenticationManager manager = configuration.getAuthenticationManager(myVcs);

      try {
        T svnAuthentication = getWithPassive(passive);
        if (svnAuthentication == null) {
          svnAuthentication = getWithActive(manager);
        }
        if (svnAuthentication == null) return false;

        if (myStoreInUsual) {
          manager.setArtificialSaving(true);
          return acknowledge(manager, svnAuthentication);
        } else {
          if (myTmpDirManager == null) {
            initTmpDir(configuration);
            myTmpDirManager = createTmpManager();
          }
          myTmpDirManager.setArtificialSaving(true);
          return acknowledge(myTmpDirManager, svnAuthentication);
        }
      }
      catch (IOException e) {
        LOG.info(e);
        VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), e.getMessage(), MessageType.ERROR);
        return false;
      }
      catch (SVNException e) {
        LOG.info(e);
        VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), e.getMessage(), MessageType.ERROR);
        return false;
      }
    }

    protected SvnAuthenticationManager createTmpManager() {
      return SvnConfiguration.createForTmpDir(myVcs.getProject(), myTempDirectory);
    }

    protected abstract T getWithPassive(SvnAuthenticationManager passive) throws SVNException;
    protected abstract T getWithActive(SvnAuthenticationManager active) throws SVNException;
    protected abstract boolean acknowledge(SvnAuthenticationManager manager, T svnAuthentication) throws SVNException;
  }

  private void initTmpDir(SvnConfiguration configuration) throws IOException {
    if (myTempDirectory == null) {
      myTempDirectory = FileUtil.createTempDirectory("tmp", "Subversion");
      FileUtil.copyDir(new File(configuration.getConfigurationDirectory()), myTempDirectory);
    }
  }

  private void doWithSubscribeToAuthProvider(SvnAuthenticationManager.ISVNAuthenticationProviderListener listener,
                                             final ThrowableRunnable<SVNException> runnable) throws SVNException {
    MessageBusConnection connection = null;
    try {
      final Project project = myVcs.getProject();
      connection = project.getMessageBus().connect(project);
      connection.subscribe(SvnAuthenticationManager.AUTHENTICATION_PROVIDER_LISTENER, listener);
      runnable.run();
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  // plus seems that we also should ask for credentials; but we didn't receive realm name yet
  private class SSLServerCertificateAuthenticator extends AbstractAuthenticator<Boolean> {
    private SVNURL myUrl;
    private String myRealm;
    private String myCertificateRealm;
    private String myCredentialsRealm;
    private Object myCertificate;
    private int myResult;
    private SVNAuthentication myAuthentication;

    protected SSLServerCertificateAuthenticator(SvnVcs vcs) {
      super(vcs);
    }

    public boolean tryAuthenticate(final SVNURL url, final String realm) {
      myUrl = url;
      myRealm = realm;
      myResult = ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
      myStoreInUsual = false;
      return tryAuthenticate();
    }

    @Override
    protected Boolean getWithPassive(SvnAuthenticationManager passive) throws SVNException {
      String stored = (String) passive.getRuntimeAuthStorage().getData("svn.ssl.server", myRealm);
      if (stored == null) return null;
      CertificateFactory cf;
      try {
        cf = CertificateFactory.getInstance("X509");
        final byte[] buffer = new byte[stored.length()];
        SVNBase64.base64ToByteArray(new StringBuffer(stored), buffer);
        myCertificate = cf.generateCertificate(new ByteArrayInputStream(buffer));
      }
      catch (CertificateException e) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, e));
      }
      myCertificateRealm = myRealm;
      return myCertificate != null ? true : null;
    }

    @Override
    protected Boolean getWithActive(final SvnAuthenticationManager active) throws SVNException {
      doWithSubscribeToAuthProvider(new SvnAuthenticationManager.ISVNAuthenticationProviderListener() {
                                      @Override
                                      public void requestClientAuthentication(String kind,
                                                                              SVNURL url,
                                                                              String realm,
                                                                              SVNErrorMessage errorMessage,
                                                                              SVNAuthentication previousAuth,
                                                                              boolean authMayBeStored,
                                                                              SVNAuthentication authentication) {
                                        if (!myUrl.equals(url)) return;
                                        myCredentialsRealm = realm;
                                        myAuthentication = authentication;
                                        if (myAuthentication != null) {
                                          myStoreInUsual &= myAuthentication.isStorageAllowed();
                                        }
                                      }

                                      @Override
                                      public void acceptServerAuthentication(SVNURL url,
                                                                             String realm,
                                                                             Object certificate,
                                                                             boolean resultMayBeStored,
                                                                             int accepted) {
                                        if (!myUrl.equals(url)) return;
                                        myCertificateRealm = realm;
                                        myCertificate = certificate;
                                        myResult = accepted;
                                      }
                                    }, new ThrowableRunnable<SVNException>() {
                                      @Override
                                      public void run() throws SVNException {
                                        myVcs.createWCClient(active).doInfo(myUrl, SVNRevision.UNDEFINED, SVNRevision.HEAD);
                                      }
                                    }
      );

      myStoreInUsual &= myCertificate != null && ISVNAuthenticationProvider.ACCEPTED == myResult;
      return ISVNAuthenticationProvider.REJECTED != myResult && myCertificate != null;
    }

    @Override
    protected boolean acknowledge(SvnAuthenticationManager manager, Boolean svnAuthentication) throws SVNException {
      // we should store certificate, if it wasn't accepted (if temporally tmp)
      if (myCertificate == null) {   // this is if certificate was stored only in passive area
        String stored = (String) manager.getRuntimeAuthStorage().getData("svn.ssl.server", myRealm);
        if (StringUtil.isEmptyOrSpaces(stored)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "No stored server certificate was found in runtime"));
        }
        CertificateFactory cf;
        try {
          cf = CertificateFactory.getInstance("X509");
          final byte[] buffer = new byte[stored.length()];
          SVNBase64.base64ToByteArray(new StringBuffer(stored), buffer);
          myCertificate = cf.generateCertificate(new ByteArrayInputStream(buffer));
        }
        catch (CertificateException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, e));
        }
        myCertificateRealm = myRealm;
      }
      if (myTempDirectory != null && myCertificate != null) {
        if (! (myCertificate instanceof X509Certificate)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can not store server certificate: " + myCertificate));
        }
        X509Certificate x509Certificate = (X509Certificate) myCertificate;
        String stored;
        try {
          stored = SVNBase64.byteArrayToBase64(x509Certificate.getEncoded());
        }
        catch (CertificateEncodingException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e));
        }
        int failures = SVNSSLUtil.getServerCertificateFailures(x509Certificate, myUrl.getHost());
        storeServerCertificate(myTempDirectory, myCertificateRealm, stored, failures);
        if (myAuthentication != null) {
          final String realm = myCredentialsRealm == null ? myCertificateRealm : myCredentialsRealm;
          return storeCredentials(manager, myAuthentication, realm);
        }
      }
      return true;
    }

    private void storeServerCertificate(final File configDir, String realm, String data, int failures) throws SVNException {
      //noinspection ResultOfMethodCallIgnored
      configDir.mkdirs();

      File file = new File(configDir, "auth/svn.ssl.server/" + SVNFileUtil.computeChecksum(realm));
      SVNHashMap map = new SVNHashMap();
      map.put("ascii_cert", data);
      map.put("svn:realmstring", realm);
      map.put("failures", Integer.toString(failures));

      SVNFileUtil.deleteFile(file);

      File tmpFile = SVNFileUtil.createUniqueFile(configDir, "auth", ".tmp", true);
      try {
        SVNWCProperties.setProperties(SVNProperties.wrap(map), file, tmpFile, SVNWCProperties.SVN_HASH_TERMINATOR);
      } finally {
        SVNFileUtil.deleteFile(tmpFile);
      }
    }
  }

  private boolean storeCredentials(SvnAuthenticationManager manager, final SVNAuthentication authentication, final String realm) throws SVNException {
    try {
      final String kind = getFromType(authentication);
      if (! acknowledgeSSL(manager, authentication)) {
        manager.acknowledgeAuthentication(true, kind, realm, null, authentication, authentication.getURL());
      }
    } catch (SvnAuthenticationManager.CredentialsSavedException e) {
      return e.isSuccess();
    }
    return true;
  }

  private class CredentialsAuthenticator extends AbstractAuthenticator<SVNAuthentication> {
    private String myKind;
    private String myRealm;
    // sometimes realm string is different (with <>), so store credentials for both strings..
    private String myRealm2;
    private SVNURL myUrl;
    private SVNAuthentication myAuthentication;
    private File myFile;

    protected CredentialsAuthenticator(SvnVcs vcs) {
      super(vcs);
    }

    public boolean tryAuthenticate(String realm, SVNURL url, File file, boolean previousFailed, boolean passwordRequest) {
      myFile = file;
      realm = realm == null ? url.getHost() : realm;
      myRealm = realm;
      myUrl = url;
      final List<String> kinds = getKinds(url, passwordRequest);
      for (String kind : kinds) {
        myKind = kind;
        if (! tryAuthenticate()) {
          return false;
        }
      }
      return true;
    }

    @Override
    protected SVNAuthentication getWithPassive(SvnAuthenticationManager passive) throws SVNException {
      final SVNAuthentication impl = getWithPassiveImpl(passive);
      if (impl != null && ! checkAuthOk(impl)) {
        clearPassiveCredentials(myRealm, myFile, impl instanceof SVNPasswordAuthentication);  //clear passive also take into acconut ssl filepath
        return null;
      }
      return impl;
    }

    private SVNAuthentication getWithPassiveImpl(SvnAuthenticationManager passive) throws SVNException {
      try {
        return passive.getFirstAuthentication(myKind, myRealm, myUrl);
      } catch (SVNCancelException e) {
        return null;
      }
    }

    private boolean checkAuthOk(SVNAuthentication authentication) {
      if (authentication instanceof SVNPasswordAuthentication && StringUtil.isEmptyOrSpaces(authentication.getUserName())) return false;
      if (authentication instanceof SVNSSLAuthentication) {
        if (StringUtil.isEmptyOrSpaces(((SVNSSLAuthentication)authentication).getPassword())) return false;
      }
      return true;
    }

    @Override
    protected SVNAuthentication getWithActive(final SvnAuthenticationManager active) throws SVNException {
      if (ISVNAuthenticationManager.SSL.equals(myKind)) {
        doWithSubscribeToAuthProvider(new SvnAuthenticationManager.ISVNAuthenticationProviderListener() {
                                        @Override
                                        public void requestClientAuthentication(String kind,
                                                                                SVNURL url,
                                                                                String realm,
                                                                                SVNErrorMessage errorMessage,
                                                                                SVNAuthentication previousAuth,
                                                                                boolean authMayBeStored,
                                                                                SVNAuthentication authentication) {
                                          if (!myUrl.equals(url)) return;
                                          myAuthentication = authentication;
                                          myRealm2 = realm;
                                          myStoreInUsual = myAuthentication != null && myAuthentication.isStorageAllowed();
                                        }

                                        @Override
                                        public void acceptServerAuthentication(SVNURL url,
                                                                               String realm,
                                                                               Object certificate,
                                                                               boolean resultMayBeStored,
                                                                               int accepted) {
                                        }
                                      }, new ThrowableRunnable<SVNException>() {
                                        @Override
                                        public void run() throws SVNException {
                                          myVcs.createWCClient(active).doInfo(myUrl, SVNRevision.UNDEFINED, SVNRevision.HEAD);
                                        }
                                      }
        );
        if (myAuthentication != null) return myAuthentication;
      }
      myAuthentication = active.getProvider().requestClientAuthentication(myKind, myUrl, myRealm, null, null, true);
      myStoreInUsual = myTempDirectory == null && myAuthentication != null && myAuthentication.isStorageAllowed();
      return myAuthentication;
    }

    @Override
    protected boolean acknowledge(SvnAuthenticationManager manager, SVNAuthentication svnAuthentication) throws SVNException {
      if (! StringUtil.isEmptyOrSpaces(myRealm2) && ! myRealm2.equals(myRealm)) {
        storeCredentials(manager, svnAuthentication, myRealm2);
      }
      return storeCredentials(manager, svnAuthentication, myRealm);
    }
  }

  private boolean acknowledgeSSL(SvnAuthenticationManager manager, SVNAuthentication svnAuthentication) throws SVNException {
    if (svnAuthentication instanceof SVNSSLAuthentication && (((SVNSSLAuthentication) svnAuthentication).getCertificateFile() != null)) {
      manager.acknowledgeForSSL(true, getFromType(svnAuthentication),
                                              ((SVNSSLAuthentication) svnAuthentication).getCertificateFile().getPath(),
                                              null, svnAuthentication);
      manager.acknowledgeAuthentication(true, getFromType(svnAuthentication),
                                        ((SVNSSLAuthentication) svnAuthentication).getCertificateFile().getPath(),
                                        null, svnAuthentication, svnAuthentication.getURL());
      return true;
    }
    return false;
  }

  private File getExistingParent(final File file) {
    File current = file;
    while (current != null) {
      if (current.exists()) return current;
      current = current.getParentFile();
    }
    return null;
  }

  private static List<String> getKinds(final SVNURL url, boolean passwordRequest) {
    if (passwordRequest || "http".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.PASSWORD);
    } else if ("https".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.SSL);
    } else if ("svn".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.PASSWORD);
    } else if (url.getProtocol().contains("svn+")) {  // todo +-
      return Arrays.asList(ISVNAuthenticationManager.SSH, ISVNAuthenticationManager.USERNAME);
    } else if ("file".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.USERNAME);
    }
    return Collections.singletonList(ISVNAuthenticationManager.USERNAME);
  }

  @Nullable
  @Override
  public File getSpecialConfigDir() {
    return myTempDirectory != null ? myTempDirectory : new File(myConfiguration.getConfigurationDirectory());
  }

  private String getFromType(SVNAuthentication authentication) {
    if (authentication instanceof SVNPasswordAuthentication) {
      return ISVNAuthenticationManager.PASSWORD;
    }
    if (authentication instanceof SVNSSHAuthentication) {
      return ISVNAuthenticationManager.SSH;
    }
    if (authentication instanceof SVNSSLAuthentication) {
      return ISVNAuthenticationManager.SSL;
    }
    if (authentication instanceof SVNUserNameAuthentication) {
      return ISVNAuthenticationManager.USERNAME;
    }
    throw new IllegalArgumentException();
  }
}
