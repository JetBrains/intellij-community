// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

public class ProxySvnAuthentication {
  private ProxySvnAuthentication() {
  }

  public static AuthenticationData proxy(final AuthenticationData in, final boolean storeAuth) {
    if (in.isStorageAllowed() == storeAuth || ( ! in.isStorageAllowed())) return in;
    return putPassedValueAsSave(in, storeAuth);
  }

  private static AuthenticationData putPassedValueAsSave(AuthenticationData in, boolean storeAuth) {
    if (in instanceof PasswordAuthenticationData) {
      return new PasswordAuthenticationData(((PasswordAuthenticationData)in).getCredentials(), storeAuth);
    }
    else if (in instanceof CertificateAuthenticationData) {
      return new CertificateAuthenticationData(((CertificateAuthenticationData)in).getCertificate(), storeAuth);
    }
    return in;
  }
}
