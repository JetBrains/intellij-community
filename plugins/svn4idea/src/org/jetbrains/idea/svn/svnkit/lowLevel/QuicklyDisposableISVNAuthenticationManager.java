/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.svnkit.lowLevel;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.net.ssl.TrustManager;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/15/12
 * Time: 3:13 PM
 */
public class QuicklyDisposableISVNAuthenticationManager extends QuicklyDisposableProxy<ISVNAuthenticationManager> implements ISVNAuthenticationManager {
  public QuicklyDisposableISVNAuthenticationManager(ISVNAuthenticationManager manager) {
    super(manager);
  }

  public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {
    getRef().setAuthenticationProvider(provider);
  }

  public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
    return getRef().getProxyManager(url);
  }

  public TrustManager getTrustManager(SVNURL url) throws SVNException {
    return getRef().getTrustManager(url);
  }

  public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
    return getRef().getFirstAuthentication(kind, realm, url);
  }

  public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException {
    return getRef().getNextAuthentication(kind, realm, url);
  }

  public void acknowledgeAuthentication(boolean accepted,
                                        String kind,
                                        String realm,
                                        SVNErrorMessage errorMessage,
                                        SVNAuthentication authentication) throws SVNException {
    getRef().acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication);
  }

  public void acknowledgeTrustManager(TrustManager manager) {
    getRef().acknowledgeTrustManager(manager);
  }

  public boolean isAuthenticationForced() {
    return getRef().isAuthenticationForced();
  }

  public int getReadTimeout(SVNRepository repository) {
    return getRef().getReadTimeout(repository);
  }

  public int getConnectTimeout(SVNRepository repository) {
    return getRef().getConnectTimeout(repository);
  }
}
