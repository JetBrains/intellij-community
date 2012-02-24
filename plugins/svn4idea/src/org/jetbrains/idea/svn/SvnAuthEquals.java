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

import com.intellij.openapi.util.Comparing;
import org.tmatesoft.svn.core.auth.*;

public class SvnAuthEquals {
  private SvnAuthEquals() {
  }

  public static boolean equals(final SVNAuthentication a1, final SVNAuthentication a2) {
    if (a1 == a2) return true;
    if (a1 == null || a2 == null) return false;
    if (! Comparing.equal(a1.getKind(), a2.getKind())) return false;
    if (! Comparing.equal(a1.getUserName(), a2.getUserName())) return false;

    final Class<? extends SVNAuthentication> a1Class = a1.getClass();
    if (! a1Class.equals(a2.getClass())) return false;

    if (SVNUserNameAuthentication.class.equals(a1Class)) return true;
    if (SVNPasswordAuthentication.class.equals(a1Class)) {
      return Comparing.equal(((SVNPasswordAuthentication) a1).getPassword(), ((SVNPasswordAuthentication) a2).getPassword());
    }
    if (SVNSSLAuthentication.class.equals(a1Class)) {
      if (! Comparing.equal(((SVNSSLAuthentication) a1).getCertificateFile(), ((SVNSSLAuthentication) a2).getCertificateFile())) return false;
      return Comparing.equal(((SVNSSLAuthentication) a1).getPassword(), ((SVNSSLAuthentication) a2).getPassword());
    }
    if (SVNSSHAuthentication.class.equals(a1Class)) {
      if (! Comparing.equal(((SVNSSHAuthentication) a1).getPrivateKeyFile(), ((SVNSSHAuthentication) a2).getPrivateKeyFile())) return false;
      if (! Comparing.equal(((SVNSSHAuthentication) a1).getPassphrase(), ((SVNSSHAuthentication) a2).getPassphrase())) return false;
      if (! Comparing.equal(((SVNSSHAuthentication) a1).getPortNumber(), ((SVNSSHAuthentication) a2).getPortNumber())) return false;
      return Comparing.equal(((SVNSSHAuthentication) a1).getPassword(), ((SVNSSHAuthentication) a2).getPassword());
    }
    return false;
  }

  public static int hashCode(final SVNAuthentication a) {
    int result = a.getKind().hashCode();
    if (a.getUserName() != null) {
      result = (31 * result) + a.getUserName().hashCode();
    }
    return result;
  }
}
