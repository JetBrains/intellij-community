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
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCProperties;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
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

  public IdeaSvnkitBasedAuthenticationCallback(SvnVcs vcs) {
    myVcs = vcs;
  }

  @Override
  public boolean authenticateFor(String realm, File file, boolean previousFailed) {
    final File base = getExistingParent(file);
    if (base == null) return false;
    final SVNURL url = SvnUtil.getCommittedURL(myVcs, base);
    if (url == null) return false;

    return new CredentialsAuthenticator(myVcs).tryAuthenticate(realm, url, file, previousFailed);
    //return tryAuthenticate(realm, url, file, previousFailed);
  }

  @Override
  public boolean acceptSSLServerCertificate(final File file) {
    final File base = getExistingParent(file);
    if (base == null) return false;
    final SVNURL url = SvnUtil.getCommittedURL(myVcs, base);
    if (url == null) return false;

    return new SSLServerCertificateAuthenticator(myVcs).tryAuthenticate(url);
  }

  @Override
  public boolean acceptSSLServerCertificate(final String url) {
    try {
      return new SSLServerCertificateAuthenticator(myVcs).tryAuthenticate(SVNURL.parseURIEncoded(url));
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
          return acknowledge(manager, svnAuthentication);
        } else {
          if (myTmpDirManager == null) {
            if (myTempDirectory == null) {
              myTempDirectory = FileUtil.createTempDirectory("tmp", "Subversion");
            }
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

  // plus seems that we also should ask for credentials; but we didn't receive realm name yet
  private class SSLServerCertificateAuthenticator extends AbstractAuthenticator<Boolean> {
    private SVNURL myUrl;
    private String myCertificateRealm;
    private String myCredentialsRealm;
    private Object myCertificate;
    private int myResult;
    private SVNAuthentication myAuthentication;

    protected SSLServerCertificateAuthenticator(SvnVcs vcs) {
      super(vcs);
    }

    public boolean tryAuthenticate(final SVNURL url) {
      myUrl = url;
      myResult = ISVNAuthenticationProvider.ACCEPTED;
      myStoreInUsual = true;
      return tryAuthenticate();
    }

    @Override
    protected Boolean getWithPassive(SvnAuthenticationManager passive) throws SVNException {
      return null;
    }

    @Override
    protected Boolean getWithActive(SvnAuthenticationManager active) throws SVNException {
      final ISVNAuthenticationProvider delegate = active.getProvider();
      try {
        active.setAuthenticationProvider(new ISVNAuthenticationProvider() {
          @Override
          public SVNAuthentication requestClientAuthentication(String kind,
                                                               SVNURL url,
                                                               String realm,
                                                               SVNErrorMessage errorMessage,
                                                               SVNAuthentication previousAuth,
                                                               boolean authMayBeStored) {
            myCredentialsRealm = realm;
            myAuthentication = delegate.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
            if (myAuthentication != null) {
              myStoreInUsual &= myAuthentication.isStorageAllowed();
            }
            return myAuthentication;
          }

          @Override
          public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
            myCertificateRealm = realm;
            myCertificate = certificate;
            myResult = delegate.acceptServerAuthentication(url, realm, certificate, resultMayBeStored);
            return myResult;
          }
        });
        final SVNInfo info = myVcs.createWCClient(active).doInfo(myUrl, SVNRevision.UNDEFINED, SVNRevision.HEAD);
        myStoreInUsual &= ISVNAuthenticationProvider.ACCEPTED == myResult;
        return ISVNAuthenticationProvider.REJECTED != myResult;
      } catch (SVNException e) {
        if (e.getErrorMessage().getErrorCode().isAuthentication()) return null;
        throw e;
      } finally {
        active.setAuthenticationProvider(delegate);
      }
    }

    @Override
    protected boolean acknowledge(SvnAuthenticationManager manager, Boolean svnAuthentication) throws SVNException {
      // we should store certificate, if it wasn't accepted (if temporally tmp)
      if (myTempDirectory != null && myCertificate != null) {
        if (! (myCertificate instanceof X509Certificate)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can not store server certificate: " + myCertificate));
        }
        X509Certificate x509Certificate = (X509Certificate) myCertificate;
        String stored = null;
        try {
          stored = SVNBase64.byteArrayToBase64(x509Certificate.getEncoded());
        }
        catch (CertificateEncodingException e) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e));
        }
        int failures = SVNSSLUtil.getServerCertificateFailures(x509Certificate, myUrl.getHost());
        storeServerCertificate(myTempDirectory, myCertificateRealm, stored, failures);
        try {
          final String kind = myAuthentication instanceof SVNPasswordAuthentication ? ISVNAuthenticationManager.PASSWORD :
            ISVNAuthenticationManager.SSL;
          final String realm = myCredentialsRealm == null ? myCertificateRealm : myCredentialsRealm;
          manager.acknowledgeAuthentication(true, kind, realm, null, myAuthentication, myUrl);
        } catch (SvnAuthenticationManager.CredentialsSavedException e) {
          return e.isSuccess();
        }
      }
      return true;
    }

    private void storeServerCertificate(final File configDir, String realm, String data, int failures) throws SVNException {
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

  private class CredentialsAuthenticator extends AbstractAuthenticator<SVNAuthentication> {
    private String myKind;
    private String myRealm;
    private SVNURL myUrl;
    private SVNAuthentication myAuthentication;

    protected CredentialsAuthenticator(SvnVcs vcs) {
      super(vcs);
    }

    public boolean tryAuthenticate(String realm, SVNURL url, File file, boolean previousFailed) {
      myRealm = realm;
      myUrl = url;
      realm = realm == null ? url.getHost() : realm;
      final List<String> kinds = getKinds(url);
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
      try {
        return passive.getFirstAuthentication(myKind, myRealm, myUrl);
      } catch (SVNCancelException e) {
        return null;
      }
    }

    @Override
    protected SVNAuthentication getWithActive(SvnAuthenticationManager active) throws SVNException {
      if (ISVNAuthenticationManager.SSL.equals(myKind)) {
        final ISVNAuthenticationProvider provider = active.getProvider();
        try {
          active.setAuthenticationProvider(new ISVNAuthenticationProvider() {
            @Override
            public SVNAuthentication requestClientAuthentication(String kind,
                                                                 SVNURL url,
                                                                 String realm,
                                                                 SVNErrorMessage errorMessage,
                                                                 SVNAuthentication previousAuth,
                                                                 boolean authMayBeStored) {
              myAuthentication = provider.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
              myStoreInUsual = myAuthentication != null && myAuthentication.isStorageAllowed();
              return myAuthentication;
            }

            @Override
            public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
              return provider.acceptServerAuthentication(url, realm, certificate, resultMayBeStored);
            }
          });
          myVcs.createWCClient(active).doInfo(myUrl, SVNRevision.UNDEFINED, SVNRevision.HEAD);

        } finally {
          active.setAuthenticationProvider(provider);
        }
        return myAuthentication;
      }
      myAuthentication = active.getProvider().requestClientAuthentication(myKind, myUrl, myRealm, null, null, true);
      myStoreInUsual = myTempDirectory == null && myAuthentication != null && myAuthentication.isStorageAllowed();
      return myAuthentication;
    }

    @Override
    protected boolean acknowledge(SvnAuthenticationManager manager, SVNAuthentication svnAuthentication) throws SVNException {
      try {
        manager.acknowledgeAuthentication(true, myKind, myRealm, null, svnAuthentication, myUrl);
      } catch (SvnAuthenticationManager.CredentialsSavedException e) {
        return e.isSuccess();
      }
      return true;
    }
  }

  private boolean tryAuthenticate(String realm, SVNURL url, File file, boolean previousFailed) {
    realm = realm == null ? url.getHost() : realm;
    // 1. get auth data
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
    final SvnAuthenticationManager passive = configuration.getPassiveAuthenticationManager(myVcs.getProject());

    final SvnAuthenticationManager manager = configuration.getAuthenticationManager(myVcs);
    final ISVNAuthenticationProvider provider = manager.getProvider();
    //final SvnInteractiveAuthenticationProvider provider = new SvnInteractiveAuthenticationProvider(myVcs, manager);

    SvnAuthenticationManager tmpDirManager = null;

    final List<String> kinds = getKinds(url);
    for (String kind : kinds) {
      try {
        boolean fromPassive = true;
        SVNAuthentication svnAuthentication = getPassiveAuthentication(realm, url, passive, kind);
        if (svnAuthentication == null) {
          fromPassive = false;
          svnAuthentication = provider.requestClientAuthentication(kind, url, realm,
                    previousFailed ? SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED) : null, null, true);
        }
        if (svnAuthentication == null) return false;

        configuration.acknowledge(kind, realm, svnAuthentication);
        if (! fromPassive && svnAuthentication.isStorageAllowed()) {
          //manager.requested(ProviderType.interactive, url, realm, kind, false);
          manager.acknowledgeAuthentication(true, kind, realm, null, svnAuthentication);
          return true;
        } else {
          if (tmpDirManager == null) {
            myTempDirectory = FileUtil.createTempDirectory("tmp", "Subversion");
            tmpDirManager = SvnConfiguration.createForTmpDir(myVcs.getProject(), myTempDirectory);
          }
          tmpDirManager.setArtificialSaving(true);
          try {
            tmpDirManager.acknowledgeAuthentication(true, kind, realm, null, svnAuthentication, url);
          } catch (SvnAuthenticationManager.CredentialsSavedException e) {
            return e.isSuccess();
          }
        }
      }
      catch (SVNException e) {
        LOG.info(e);
        VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), e.getMessage(), MessageType.ERROR);
        return false;
      }
      catch (IOException e) {
        LOG.info(e);
        VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), e.getMessage(), MessageType.ERROR);
        return false;
      }
    }
    return false;
  }

  private SVNAuthentication getPassiveAuthentication(String realm, SVNURL url, SvnAuthenticationManager passive, String kind)
    throws SVNException {
    try {
      return passive.getFirstAuthentication(kind, realm, url);
    } catch (SVNCancelException e) {
      return null;
    }
  }

  private File getExistingParent(final File file) {
    File current = file;
    while (current != null) {
      if (current.exists()) return current;
      current = current.getParentFile();
    }
    return null;
  }

  private static List<String> getKinds(final SVNURL url) {
    if ("http".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.PASSWORD);
    } else if ("https".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.SSL);
    } else if ("svn".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.PASSWORD);
    } else if ("svn+ssh".equals(url.getProtocol())) {
      return Arrays.asList(ISVNAuthenticationManager.SSH, ISVNAuthenticationManager.USERNAME);
    } else if ("file".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.USERNAME);
    }
    return Collections.singletonList(ISVNAuthenticationManager.USERNAME);
  }

  @Nullable
  @Override
  public File getSpecialConfigDir() {
    return myTempDirectory;
  }
}
