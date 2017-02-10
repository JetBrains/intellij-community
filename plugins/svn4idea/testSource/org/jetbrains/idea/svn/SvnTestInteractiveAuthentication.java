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
package org.jetbrains.idea.svn;

import com.intellij.util.containers.Convertor;
import org.jetbrains.idea.svn.auth.ProviderType;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 2/11/13
* Time: 4:08 PM
*/
public class SvnTestInteractiveAuthentication implements ISVNAuthenticationProvider {
  private final SvnAuthenticationManager myManager;
  private boolean mySaveData;
  private final Map<String, Convertor<SVNURL, SVNAuthentication>> myData;

  public SvnTestInteractiveAuthentication(SvnAuthenticationManager manager) {
    myManager = manager;
    mySaveData = true;
    myData = new HashMap<>();
  }

  public void setSaveData(boolean saveData) {
    mySaveData = saveData;
  }

  @Override
  public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
    return ISVNAuthenticationProvider.REJECTED;
  }

  public void addAuthentication(final String kind, final Convertor<SVNURL, SVNAuthentication> authentication) {
    myData.put(kind, authentication);
  }

  @Override
  public SVNAuthentication requestClientAuthentication(String kind,
                                                       SVNURL url,
                                                       String realm,
                                                       SVNErrorMessage errorMessage,
                                                       SVNAuthentication previousAuth,
                                                       boolean authMayBeStored) {
    authMayBeStored = authMayBeStored && mySaveData;
    Convertor<SVNURL, SVNAuthentication> convertor = myData.get(kind);
    SVNAuthentication result = convertor == null ? null : convertor.convert(url);
    if (result == null) {
      if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
        result = new SVNUserNameAuthentication("username", authMayBeStored);
      } else if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
        result = new SVNPasswordAuthentication("username", "abc", authMayBeStored, url, false);
      } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
        result = new SVNSSHAuthentication("username", "abc", -1, authMayBeStored, url, false);
      } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
        result = new SVNSSLAuthentication(new File("aaa"), "abc", authMayBeStored, url, false);
      }
    }
    if (! ISVNAuthenticationManager.USERNAME.equals(kind)) {
      myManager.requested(ProviderType.interactive, url, realm, kind, result == null);
    }
    return result;
  }
}
