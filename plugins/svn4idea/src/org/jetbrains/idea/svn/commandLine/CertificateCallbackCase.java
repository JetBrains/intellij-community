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
package org.jetbrains.idea.svn.commandLine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class CertificateCallbackCase extends AuthCallbackCase {

  private static final String CERTIFICATE_ERROR = "Error validating server certificate for";
  private static final String UNTRUSTED_SERVER_CERTIFICATE = "Server SSL certificate untrusted";
  private static final String CERTIFICATE_VERIFICATION_FAILED = "certificate verification failed";
  private static final String CERTIFICATE_VERIFICATION_FAILED_ISSUER_NOT_TRUSTED = "certificate verification failed: issuer is not trusted";

  private boolean accepted;

  CertificateCallbackCase(@NotNull AuthenticationService authenticationService, SVNURL url) {
    super(authenticationService, url);
  }

  @Override
  public boolean canHandle(String error) {
    return error.startsWith(CERTIFICATE_ERROR) ||
           // https one-way protocol untrusted server certificate
           error.contains(UNTRUSTED_SERVER_CERTIFICATE) ||
           // valid but untrusted certificates - "issuer is not trusted" error - for both 1.7 and 1.8.
           // Implementation not based on SVNKit does not persist credentials to emulate situation as if credentials were cached by
           // Subversion. And in "--non-interactive" mode we could only make Subversion accept untrusted, but not invalid certificate.
           // So we explicitly check that verification failure is only "issuer is not trusted". If certificate has some other failures,
           // command will end with error.
           isValidButUntrustedCertificate(error);
  }

  @Override
  public boolean getCredentials(final String errText) throws SvnBindException {
    if (myAuthenticationService.acceptSSLServerCertificate(myUrl)) {
      accepted = true;
      return true;
    }
    throw new SvnBindException("Server SSL certificate rejected");
  }

  @Override
  public void updateParameters(@NotNull Command command) {
    if (accepted) {
      command.put("--trust-server-cert");
      // force --non-interactive as it is required by --trust-server-cert, but --non-interactive is not default mode for 1.7 or earlier
      command.put("--non-interactive");
    }
  }

  public static boolean isValidButUntrustedCertificate(@NotNull String error) {
    return error.contains(CERTIFICATE_VERIFICATION_FAILED_ISSUER_NOT_TRUSTED);
  }

  public static boolean isCertificateVerificationFailed(@NotNull String error) {
    return error.contains(CERTIFICATE_VERIFICATION_FAILED);
  }
}
