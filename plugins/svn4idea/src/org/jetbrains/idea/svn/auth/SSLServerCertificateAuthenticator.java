/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCProperties;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.cert.*;

/**
* @author Konstantin Kolosovsky.
*/ // plus seems that we also should ask for credentials; but we didn't receive realm name yet
class SSLServerCertificateAuthenticator extends AbstractAuthenticator {
  private String myCertificateRealm;
  private String myCredentialsRealm;
  private Object myCertificate;
  private int myResult;
  private SVNAuthentication myAuthentication;

  SSLServerCertificateAuthenticator(@NotNull AuthenticationService authenticationService, @NotNull SVNURL url, String realm) {
    super(authenticationService, url, realm);
  }

  @Override
  public boolean tryAuthenticate() {
    myResult = ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
    myStoreInUsual = false;
    return super.tryAuthenticate();
  }

  @Override
  protected boolean getWithPassive(SvnAuthenticationManager passive) throws SVNException {
    String stored = (String)passive.getRuntimeAuthStorage().getData("svn.ssl.server", myRealm);
    if (stored == null) return false;

    myCertificate = createCertificate(stored);
    myCertificateRealm = myRealm;
    return true;
  }

  @Override
  public void requestClientAuthentication(SVNURL url, String realm, SVNAuthentication authentication) {
    if (!myUrl.equals(url)) return;
    myCredentialsRealm = realm;
    myAuthentication = authentication;
    if (myAuthentication != null) {
      myStoreInUsual &= myAuthentication.isStorageAllowed();
    }
  }

  @Override
  public void acceptServerAuthentication(SVNURL url, String realm, Object certificate, @MagicConstant int acceptResult) {
    if (!myUrl.equals(url)) return;
    myCertificateRealm = realm;
    myCertificate = certificate;
    myResult = acceptResult;
  }

  @Override
  protected boolean afterAuthCall() {
    myStoreInUsual &= myCertificate != null && ISVNAuthenticationProvider.ACCEPTED == myResult;
    // TODO: Previous code always returned not null value, so Boolean == null check was always false in tryAuthenticate().
    // TODO: This was most likely error in code - check once again.
    return ISVNAuthenticationProvider.REJECTED != myResult && myCertificate != null;
  }

  @Override
  protected boolean acknowledge(SvnAuthenticationManager manager) throws SVNException {
    // we should store certificate, if it wasn't accepted (if temporally tmp)
    if (myCertificate == null) {   // this is if certificate was stored only in passive area
      String stored = (String)manager.getRuntimeAuthStorage().getData("svn.ssl.server", myRealm);
      if (StringUtil.isEmptyOrSpaces(stored)) {
        throw new SVNException(
          SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "No stored server certificate was found in runtime"));
      }
      myCertificate = createCertificate(stored);
      myCertificateRealm = myRealm;
    }
    if (myAuthenticationService.getTempDirectory() != null && myCertificate != null) {
      storeServerCertificate();

      if (myAuthentication != null) {
        final String realm = myCredentialsRealm == null ? myCertificateRealm : myCredentialsRealm;
        return storeCredentials(manager, myAuthentication, realm);
      }
    }
    return true;
  }

  @NotNull
  private Certificate createCertificate(@NotNull String stored) throws SVNException {
    CertificateFactory factory;
    try {
      factory = CertificateFactory.getInstance("X509");
      final byte[] buffer = new byte[stored.length()];
      SVNBase64.base64ToByteArray(new StringBuffer(stored), buffer);

      return factory.generateCertificate(new ByteArrayInputStream(buffer));
    }
    catch (CertificateException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, e));
    }
  }

  private void storeServerCertificate() throws SVNException {
    if (!(myCertificate instanceof X509Certificate)) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can not store server certificate: " + myCertificate));
    }
    X509Certificate x509Certificate = (X509Certificate)myCertificate;
    String stored;
    try {
      stored = SVNBase64.byteArrayToBase64(x509Certificate.getEncoded());
    }
    catch (CertificateEncodingException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e));
    }

    int failures = SVNSSLUtil.getServerCertificateFailures(x509Certificate, myUrl.getHost());
    storeServerCertificate(myAuthenticationService.getTempDirectory(), myCertificateRealm, stored, failures);
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
    }
    finally {
      SVNFileUtil.deleteFile(tmpFile);
    }
  }
}
