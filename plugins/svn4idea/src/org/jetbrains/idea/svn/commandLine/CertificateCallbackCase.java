// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AuthenticationService;

/**
 * @author Konstantin Kolosovsky.
 */
public class CertificateCallbackCase extends AuthCallbackCase {

  private static final String CERTIFICATE_ERROR = "Error validating server certificate for";
  private static final String UNTRUSTED_SERVER_CERTIFICATE = "Server SSL certificate untrusted";
  private static final String CERTIFICATE_VERIFICATION_FAILED = "certificate verification failed";
  private static final String CERTIFICATE_VERIFICATION_FAILED_ISSUER_NOT_TRUSTED = "certificate verification failed: issuer is not trusted";

  private boolean accepted;

  CertificateCallbackCase(@NotNull AuthenticationService authenticationService, Url url) {
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
