// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;

public class ProxySvnAuthentication {
  private ProxySvnAuthentication() {
  }

  public static SVNAuthentication proxy(final SVNAuthentication in, final boolean storeAuth) {
    if (in.isStorageAllowed() == storeAuth || ( ! in.isStorageAllowed())) return in;
    return putPassedValueAsSave(in, storeAuth);
  }

  private static SVNAuthentication putPassedValueAsSave(SVNAuthentication in, boolean storeAuth) {
    final String userName = in.getUserName();
    if (in instanceof SVNPasswordAuthentication) {
      return new SVNPasswordAuthentication(userName, ((SVNPasswordAuthentication)in).getPassword(),
                                           storeAuth, in.getURL(), in.isPartial());
    } else if (in instanceof SVNSSLAuthentication) {
      final SVNSSLAuthentication svnsslAuthentication = (SVNSSLAuthentication)in;
      if (SVNSSLAuthentication.MSCAPI.equals(svnsslAuthentication.getSSLKind())) {
        return new SVNSSLAuthentication(SVNSSLAuthentication.MSCAPI, svnsslAuthentication.getAlias(), storeAuth, in.getURL(), in.isPartial());
      } else {
        return new SVNSSLAuthentication(svnsslAuthentication.getCertificateFile(), svnsslAuthentication.getPassword(),
                                        storeAuth, svnsslAuthentication.getURL(), svnsslAuthentication.isPartial());
      }
    } else if (in instanceof SVNUserNameAuthentication) {
      return new SVNUserNameAuthentication(in.getUserName(), storeAuth, in.getURL(), in.isPartial());
    }
    return in;
  }
}
