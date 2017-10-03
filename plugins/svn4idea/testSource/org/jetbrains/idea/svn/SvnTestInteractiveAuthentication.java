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
package org.jetbrains.idea.svn;

import com.intellij.util.containers.Convertor;
import org.jetbrains.idea.svn.auth.AcceptResult;
import org.jetbrains.idea.svn.auth.AuthenticationProvider;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SvnTestInteractiveAuthentication implements AuthenticationProvider {
  private boolean mySaveData;
  private final Map<String, Convertor<SVNURL, SVNAuthentication>> myData;

  public SvnTestInteractiveAuthentication() {
    mySaveData = true;
    myData = new HashMap<>();
  }

  public void setSaveData(boolean saveData) {
    mySaveData = saveData;
  }

  @Override
  public AcceptResult acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean canCache) {
    return AcceptResult.REJECTED;
  }

  public void addAuthentication(final String kind, final Convertor<SVNURL, SVNAuthentication> authentication) {
    myData.put(kind, authentication);
  }

  @Override
  public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, boolean canCache) {
    canCache = canCache && mySaveData;
    Convertor<SVNURL, SVNAuthentication> convertor = myData.get(kind);
    SVNAuthentication result = convertor == null ? null : convertor.convert(url);
    if (result == null) {
      if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
        result = new SVNPasswordAuthentication("username", "abc", canCache, url, false);
      } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
        result = new SVNSSLAuthentication(new File("aaa"), "abc", canCache, url, false);
      }
    }
    return result;
  }
}
