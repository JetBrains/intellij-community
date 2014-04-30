/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.tmatesoft.svn.core.auth.*;

/**
 * @author irengrig
 *         Date: 1/24/11
 *         Time: 6:14 PM
 */
public class ProxySvnAuthentication {
  private ProxySvnAuthentication() {
  }

  public static SVNAuthentication proxy(final SVNAuthentication in, final boolean storeAuth, boolean forceSaving) {
    if (forceSaving && storeAuth) {
      return putPassedValueAsSave(in, forceSaving);
    }
    if (in.isStorageAllowed() == storeAuth || ( ! in.isStorageAllowed())) return in;
    return putPassedValueAsSave(in, storeAuth);
  }

  private static SVNAuthentication putPassedValueAsSave(SVNAuthentication in, boolean storeAuth) {
    final String userName = in.getUserName();
    if (in instanceof SVNPasswordAuthentication) {
      return new SVNPasswordAuthentication(userName, ((SVNPasswordAuthentication)in).getPassword(),
                                           storeAuth, in.getURL(), in.isPartial());
    } else if (in instanceof SVNSSHAuthentication) {
      final SVNSSHAuthentication svnsshAuthentication = (SVNSSHAuthentication)in;
      if (svnsshAuthentication.hasPrivateKey()) {
        return new SVNSSHAuthentication(userName, svnsshAuthentication.getPrivateKeyFile(), svnsshAuthentication.getPassphrase(),
                                        svnsshAuthentication.getPortNumber(), storeAuth, in.getURL(), in.isPartial());
      } else {
        return new SVNSSHAuthentication(userName, svnsshAuthentication.getPassword(), svnsshAuthentication.getPortNumber(),
                                        storeAuth, in.getURL(), in.isPartial());
      }
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
