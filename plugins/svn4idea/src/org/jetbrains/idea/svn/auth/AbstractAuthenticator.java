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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.messages.MessageBusConnection;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.IOException;

/**
 * @author Konstantin Kolosovsky.
 */
abstract class AbstractAuthenticator {

  private static final Logger LOG = Logger.getInstance(AbstractAuthenticator.class);

  @NotNull protected final AuthenticationService myAuthenticationService;
  @NotNull protected final SvnVcs myVcs;
  @NotNull protected final SVNURL myUrl;
  protected final String myRealm;
  protected boolean myStoreInUsual;
  protected SvnAuthenticationManager myTmpDirManager;

  AbstractAuthenticator(@NotNull AuthenticationService authenticationService, @NotNull SVNURL url, String realm) {
    myAuthenticationService = authenticationService;
    myVcs = myAuthenticationService.getVcs();
    myUrl = url;
    myRealm = realm;
  }

  protected boolean tryAuthenticate() {
    final SvnAuthenticationManager passive = myVcs.getSvnConfiguration().getPassiveAuthenticationManager(myVcs.getProject());
    final SvnAuthenticationManager active = myVcs.getSvnConfiguration().getAuthenticationManager(myVcs);

    try {
      boolean authenticated = getWithPassive(passive) || (myAuthenticationService.isActive() && getWithActive(active));
      if (!authenticated) return false;

      SvnAuthenticationManager manager = myStoreInUsual ? active : createTmpManager();
      manager.setArtificialSaving(true);
      return acknowledge(manager);
    }
    catch (IOException e) {
      LOG.info(e);
      VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), e.getMessage(), MessageType.ERROR);
      return false;
    }
    catch (SVNException e) {
      LOG.info(e);
      VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), e.getMessage(), MessageType.ERROR);
      return false;
    }
  }

  @NotNull
  protected SvnAuthenticationManager createTmpManager() throws IOException {
    if (myTmpDirManager == null) {
      myAuthenticationService.initTmpDir(myVcs.getSvnConfiguration());
      myTmpDirManager = new SvnAuthenticationManager(myVcs.getProject(), myAuthenticationService.getTempDirectory());
      myTmpDirManager.setRuntimeStorage(SvnConfiguration.RUNTIME_AUTH_CACHE);
      myTmpDirManager.setAuthenticationProvider(new SvnInteractiveAuthenticationProvider(myVcs, myTmpDirManager));
    }

    return myTmpDirManager;
  }

  protected boolean getWithActive(SvnAuthenticationManager active) throws SVNException {
    MessageBusConnection connection = null;
    try {
      final Project project = myVcs.getProject();
      connection = project.getMessageBus().connect(project);
      connection.subscribe(SvnAuthenticationManager.AUTHENTICATION_PROVIDER_LISTENER, new MyAuthenticationProviderListener());

      makeAuthCall(active);
    }
    finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

    return afterAuthCall();
  }

  protected void makeAuthCall(@NotNull SvnAuthenticationManager manager) throws SVNException {
    myVcs.getSvnKitManager().createWCClient(manager).doInfo(myUrl, SVNRevision.UNDEFINED, SVNRevision.HEAD);
  }

  protected void acceptServerAuthentication(SVNURL url, String realm, Object certificate, @MagicConstant int acceptResult) {
  }

  public void requestClientAuthentication(SVNURL url, String realm, SVNAuthentication authentication) {
  }

  protected abstract boolean afterAuthCall();

  protected abstract boolean getWithPassive(SvnAuthenticationManager passive) throws SVNException;

  protected abstract boolean acknowledge(SvnAuthenticationManager manager) throws SVNException;

  private class MyAuthenticationProviderListener implements SvnAuthenticationManager.ISVNAuthenticationProviderListener {
    @Override
    public void requestClientAuthentication(String kind,
                                            SVNURL url,
                                            String realm,
                                            SVNErrorMessage errorMessage,
                                            SVNAuthentication previousAuth,
                                            boolean authMayBeStored,
                                            SVNAuthentication authentication) {
      AbstractAuthenticator.this.requestClientAuthentication(url, realm, authentication);
    }

    @Override
    public void acceptServerAuthentication(SVNURL url,
                                           String realm,
                                           Object certificate,
                                           boolean resultMayBeStored,
                                           @MagicConstant int acceptResult) {
      AbstractAuthenticator.this.acceptServerAuthentication(url, realm, certificate, acceptResult);
    }
  }

  protected static boolean storeCredentials(@NotNull SvnAuthenticationManager manager, final SVNAuthentication authentication, String realm)
    throws SVNException {
    try {
      if (authentication instanceof SVNSSLAuthentication && (((SVNSSLAuthentication)authentication).getCertificateFile() != null)) {
        manager.acknowledgeForSSL(true, authentication);
        realm = ((SVNSSLAuthentication)authentication).getCertificateFile().getPath();
      }
      manager.acknowledgeAuthentication(true, getFromType(authentication), realm, null, authentication, authentication.getURL());
    }
    catch (SvnAuthenticationManager.CredentialsSavedException e) {
      return e.isSuccess();
    }
    return true;
  }

  @NotNull
  private static String getFromType(SVNAuthentication authentication) {
    if (authentication instanceof SVNPasswordAuthentication) {
      return ISVNAuthenticationManager.PASSWORD;
    }
    if (authentication instanceof SVNSSHAuthentication) {
      return ISVNAuthenticationManager.SSH;
    }
    if (authentication instanceof SVNSSLAuthentication) {
      return ISVNAuthenticationManager.SSL;
    }
    if (authentication instanceof SVNUserNameAuthentication) {
      return ISVNAuthenticationManager.USERNAME;
    }
    throw new IllegalArgumentException();
  }
}
