/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;

import java.util.List;

/**
 * TODO: Do not delete for now - probably some parts of logic could be reused.
 *
 * @author Konstantin Kolosovsky.
 */
@SuppressWarnings("UnusedDeclaration")
class CredentialsAuthenticator extends AbstractAuthenticator {
  private String myKind;
  // sometimes realm string is different (with <>), so store credentials for both strings..
  private String myRealm2;
  private SVNAuthentication myAuthentication;

  CredentialsAuthenticator(@NotNull AuthenticationService authenticationService, @NotNull SVNURL url, @Nullable String realm) {
    super(authenticationService, url, realm == null ? url.getHost() : realm);
  }

  public boolean tryAuthenticate(boolean passwordRequest) {
    final List<String> kinds = AuthenticationService.getKinds(myUrl, passwordRequest);
    for (String kind : kinds) {
      myKind = kind;
      if (!tryAuthenticate()) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected boolean getWithPassive(SvnAuthenticationManager passive) throws SVNException {
    myAuthentication = getWithPassiveImpl(passive);
    if (myAuthentication != null && !checkAuthOk(myAuthentication)) {
      //clear passive also take into account ssl file path
      myAuthenticationService.clearPassiveCredentials(myRealm, myUrl, myAuthentication instanceof SVNPasswordAuthentication);
      myAuthentication = null;
    }
    return myAuthentication != null;
  }

  private SVNAuthentication getWithPassiveImpl(SvnAuthenticationManager passive) throws SVNException {
    try {
      return passive.getFirstAuthentication(myKind, myRealm, myUrl);
    }
    catch (SVNCancelException e) {
      return null;
    }
  }

  private boolean checkAuthOk(SVNAuthentication authentication) {
    if (authentication instanceof SVNPasswordAuthentication && StringUtil.isEmptyOrSpaces(authentication.getUserName())) return false;
    if (authentication instanceof SVNSSLAuthentication) {
      if (StringUtil.isEmptyOrSpaces(((SVNSSLAuthentication)authentication).getPassword())) return false;
    }
    return true;
  }

  @Override
  protected boolean getWithActive(final SvnAuthenticationManager active) throws SVNException {
    if (ISVNAuthenticationManager.SSL.equals(myKind)) {
      if (super.getWithActive(active)) return true;
    }
    myAuthentication = active.getProvider().requestClientAuthentication(myKind, myUrl, myRealm, null, null, true);
    myStoreInUsual = myAuthenticationService.getTempDirectory() == null && myAuthentication != null && myAuthentication.isStorageAllowed();

    return myAuthentication != null;
  }

  public void requestClientAuthentication(SVNURL url, String realm, SVNAuthentication authentication) {
    if (!myUrl.equals(url)) return;
    myAuthentication = authentication;
    myRealm2 = realm;
    myStoreInUsual = myAuthentication != null && myAuthentication.isStorageAllowed();
  }

  @Override
  protected boolean afterAuthCall() {
    return myAuthentication != null;
  }

  @Override
  protected boolean acknowledge(SvnAuthenticationManager manager) throws SVNException {
    if (!StringUtil.isEmptyOrSpaces(myRealm2) && !myRealm2.equals(myRealm)) {
      storeCredentials(manager, myAuthentication, myRealm2);
    }
    return storeCredentials(manager, myAuthentication, myRealm);
  }
}
