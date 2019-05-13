// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.util.containers.Convertor;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.*;

import java.util.HashMap;
import java.util.Map;

public class SvnTestInteractiveAuthentication implements AuthenticationProvider {
  private boolean mySaveData;
  private final Map<String, Convertor<Url, AuthenticationData>> myData;

  public SvnTestInteractiveAuthentication() {
    mySaveData = true;
    myData = new HashMap<>();
  }

  public void setSaveData(boolean saveData) {
    mySaveData = saveData;
  }

  @Override
  public AcceptResult acceptServerAuthentication(Url url, String realm, Object certificate, boolean canCache) {
    return AcceptResult.REJECTED;
  }

  public void addAuthentication(final String kind, final Convertor<Url, AuthenticationData> authentication) {
    myData.put(kind, authentication);
  }

  @Override
  public AuthenticationData requestClientAuthentication(String kind, Url url, String realm, boolean canCache) {
    canCache = canCache && mySaveData;
    Convertor<Url, AuthenticationData> convertor = myData.get(kind);
    AuthenticationData result = convertor == null ? null : convertor.convert(url);
    if (result == null) {
      if (SvnAuthenticationManager.PASSWORD.equals(kind)) {
        result = new PasswordAuthenticationData("username", "abc", canCache);
      }
      else if (SvnAuthenticationManager.SSL.equals(kind)) {
        result = new CertificateAuthenticationData("aaa", "abc".toCharArray(), canCache);
      }
    }
    return result;
  }
}
