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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class CertificateCallbackCase extends AuthCallbackCase {

  private static final String CERTIFICATE_ERROR = "Error validating server certificate for";
  private static final String UNTRUSTED_SERVER_CERTIFICATE = "Server SSL certificate untrusted";
  private static final String CERTIFICATE_VERIFICATION_FAILED = "certificate verification failed";

  private boolean accepted;

  CertificateCallbackCase(@NotNull AuthenticationCallback callback, SVNURL url) {
    super(callback, url);
  }

  @Override
  public boolean canHandle(String error) {
    return error.startsWith(CERTIFICATE_ERROR) ||
           // https one-way protocol untrusted server certificate
           error.contains(UNTRUSTED_SERVER_CERTIFICATE) ||
           // for instance, certificate issued for a different hostname, issuer is not trusted - for both 1.7 and 1.8
           error.contains(CERTIFICATE_VERIFICATION_FAILED);
  }

  @Override
  public boolean getCredentials(final String errText) throws SvnBindException {
    String realm = getRealm(errText);

    if (!errText.startsWith(CERTIFICATE_ERROR)) {
      // if we do not have explicit realm in error message - use server url and not full repository url
      // as SVNKit lifecycle resolves ssl realm (for saving certificate to runtime storage) as server url
      SVNURL serverUrl = getServerUrl(realm);
      realm = serverUrl != null ? serverUrl.toString() : realm;
    }

    if (!myTried && myAuthenticationCallback.acceptSSLServerCertificate(myUrl, realm)) {
      accepted = true;
      myTried = true;
      return true;
    }
    throw new SvnBindException("Server SSL certificate rejected");
  }

  private static String getRealm(String errText) throws SvnBindException {
    String realm = errText;
    final int idx1 = realm.indexOf('\'');
    if (idx1 == -1) {
      throw new SvnBindException("Can not detect authentication realm name: " + errText);
    }
    final int idx2 = realm.indexOf('\'', idx1 + 1);
    if (idx2 == -1) {
      throw new SvnBindException("Can not detect authentication realm name: " + errText);
    }
    realm = realm.substring(idx1 + 1, idx2);
    return realm;
  }

  @Override
  public void updateParameters(@NotNull Command command) {
    if (accepted) {
      command.put("--trust-server-cert");
      // force --non-interactive as it is required by --trust-server-cert, but --non-interactive is not default mode for 1.7 or earlier
      command.put("--non-interactive");
    }
  }

  private SVNURL getServerUrl(String realm) {
    SVNURL result = parseUrl(realm);

    while (result != null && !StringUtil.isEmpty(result.getPath())) {
      try {
        result = result.removePathTail();
      }
      catch (SVNException e) {
        result = null;
      }
    }

    return result;
  }
}
