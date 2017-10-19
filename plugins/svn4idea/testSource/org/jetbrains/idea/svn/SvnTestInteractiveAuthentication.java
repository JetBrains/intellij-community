// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.util.containers.Convertor;
import org.jetbrains.idea.svn.auth.*;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;

import java.util.HashMap;
import java.util.Map;

public class SvnTestInteractiveAuthentication implements AuthenticationProvider {
  private boolean mySaveData;
  private final Map<String, Convertor<SVNURL, AuthenticationData>> myData;

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

  public void addAuthentication(final String kind, final Convertor<SVNURL, AuthenticationData> authentication) {
    myData.put(kind, authentication);
  }

  @Override
  public AuthenticationData requestClientAuthentication(String kind, SVNURL url, String realm, boolean canCache) {
    canCache = canCache && mySaveData;
    Convertor<SVNURL, AuthenticationData> convertor = myData.get(kind);
    AuthenticationData result = convertor == null ? null : convertor.convert(url);
    if (result == null) {
      if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
        result = new PasswordAuthenticationData("username", "abc", canCache);
      } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
        result = new CertificateAuthenticationData("aaa", "abc".toCharArray(), canCache);
      }
    }
    return result;
  }
}
