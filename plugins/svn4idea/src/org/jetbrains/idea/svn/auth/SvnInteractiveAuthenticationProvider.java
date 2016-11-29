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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.WaitForProgressToShow;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.ConnectorFactory;
import com.jcraft.jsch.agentproxy.TrileadAgentProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.*;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.wc.ISVNHostOptions;

import java.io.File;
import java.security.cert.X509Certificate;

public class SvnInteractiveAuthenticationProvider implements ISVNAuthenticationProvider {
  private static final Logger LOG = Logger.getInstance(SvnInteractiveAuthenticationProvider.class);
  private final Project myProject;
  private static final ThreadLocal<MyCallState> myCallState = new ThreadLocal<>();
  private final SvnAuthenticationManager myManager;

  public SvnInteractiveAuthenticationProvider(final SvnVcs vcs, SvnAuthenticationManager manager) {
    myManager = manager;
    myProject = vcs.getProject();
  }

  public static void clearCallState() {
    myCallState.set(null);
  }

  public static boolean wasCalled() {
    return myCallState.get() != null && myCallState.get().isWasCalled();
  }

  public static boolean wasCancelled() {
    return myCallState.get() != null && myCallState.get().isWasCancelled();
  }

  public SVNAuthentication requestClientAuthentication(final String kind,
                                                       final SVNURL url,
                                                       final String realm,
                                                       final SVNErrorMessage errorMessage,
                                                       final SVNAuthentication previousAuth,
                                                       final boolean authMayBeStored) {
    final MyCallState callState = new MyCallState(true, false);
    myCallState.set(callState);
    // once we came here, we don't know _correct_ auth todo +-
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    configuration.clearCredentials(kind, realm);

    final SVNAuthentication[] result = new SVNAuthentication[1];
    Runnable command = null;

    final boolean authCredsOn = authMayBeStored && myManager.getHostOptionsProvider().getHostOptions(url).isAuthStorageEnabled();

    final String userName =
      previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : myManager.getDefaultUsername(kind, url);
    if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {// || ISVNAuthenticationManager.USERNAME.equals(kind)) {
      command = new Runnable() {
        public void run() {
          SimpleCredentialsDialog dialog = new SimpleCredentialsDialog(myProject);
          dialog.setup(realm, userName, authCredsOn);
          setTitle(dialog, errorMessage);
          if (dialog.showAndGet()) {
            result[0] = new SVNPasswordAuthentication(dialog.getUserName(), dialog.getPassword(), dialog.isSaveAllowed(), url, false);
          }
        }
      };
    }
    else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return new SVNUserNameAuthentication(userName, false);
      }
      command = new Runnable() {
        public void run() {
          UserNameCredentialsDialog dialog = new UserNameCredentialsDialog(myProject);
          dialog.setup(realm, userName, authCredsOn);
          setTitle(dialog, errorMessage);
          if (dialog.showAndGet()) {
            result[0] = new SVNUserNameAuthentication(dialog.getUserName(), dialog.isSaveAllowed(), url, false);
          }
        }
      };
    }
    else if (ISVNAuthenticationManager.SSH.equals(kind)) {
      // In current implementation, pageant connector available = operating system is Windows.
      // So "ssh agent" option will be always available on Windows, even if pageant is not running.
      final Connector agentConnector = createSshAgentConnector();
      final boolean isAgentAvailable = agentConnector != null && agentConnector.isAvailable();

      command = new Runnable() {
        public void run() {
          SSHCredentialsDialog dialog = new SSHCredentialsDialog(myProject, realm, userName, authCredsOn, url.getPort(), isAgentAvailable);
          setTitle(dialog, errorMessage);
          if (dialog.showAndGet()) {
            int port = dialog.getPortNumber();
            if (dialog.isSshAgentSelected()) {
              if (agentConnector != null) {
                result[0] =
                  new SVNSSHAuthentication(dialog.getUserName(), new TrileadAgentProxy(agentConnector), port, url, false);
              }
            }
            else if (dialog.getKeyFile() != null && dialog.getKeyFile().trim().length() > 0) {
              String passphrase = dialog.getPassphrase();
              if (passphrase != null && passphrase.length() == 0) {
                passphrase = null;
              }
              result[0] =
                new SVNSSHAuthentication(dialog.getUserName(), new File(dialog.getKeyFile()), passphrase, port, dialog.isSaveAllowed(), url,
                                         false);
            }
            else {
              result[0] = new SVNSSHAuthentication(dialog.getUserName(), dialog.getPassword(), port, dialog.isSaveAllowed(), url, false);
            }
          }
        }
      };
    } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
      command = new Runnable() {
        public void run() {
          final ISVNHostOptions options = myManager.getHostOptionsProvider().getHostOptions(url);
          final String file = options.getSSLClientCertFile();
          final SSLCredentialsDialog dialog = new SSLCredentialsDialog(myProject, realm, authCredsOn);
          if (!StringUtil.isEmptyOrSpaces(file)) {
            dialog.setFile(file);
          }
          setTitle(dialog, errorMessage);
          if (dialog.showAndGet()) {
            result[0] = new SVNSSLAuthentication(new File(dialog.getCertificatePath()), String.valueOf(dialog.getCertificatePassword()),
                                                 dialog.getSaveAuth(), url, false);
          }
        }
      };
    }

    if (command != null) {
      showAndWait(command);
      log("3 authentication result: " + result[0]);
    }

    final boolean wasCanceled = result[0] == null;
    callState.setWasCancelled(wasCanceled);
    myManager.requested(ProviderType.interactive, url, realm, kind, wasCanceled);
    return result[0];
  }

  @Nullable
  private static Connector createSshAgentConnector() {
    Connector result = null;

    try {
      result = ConnectorFactory.getDefault().createConnector();
    }
    catch (AgentProxyException e) {
      LOG.info("Could not create ssh agent connector", e);
    }

    return result;
  }

  private static void setTitle(@NotNull DialogWrapper dialog, @Nullable SVNErrorMessage errorMessage) {
    dialog.setTitle(errorMessage == null
                    ? SvnBundle.message("dialog.title.authentication.required")
                    : SvnBundle.message("dialog.title.authentication.required.was.failed"));
  }

  public int acceptServerAuthentication(final SVNURL url, String realm, final Object certificate, final boolean resultMayBeStored) {
    final int[] result = new int[1];
    Runnable command;
    if (certificate instanceof X509Certificate || certificate instanceof String) {
      command = new Runnable() {
        public void run() {
          ServerSSLDialog dialog = certificate instanceof X509Certificate
                                   ? new ServerSSLDialog(myProject, (X509Certificate)certificate, resultMayBeStored)
                                   : new ServerSSLDialog(myProject, (String)certificate, resultMayBeStored);
          dialog.show();
          result[0] = dialog.getResult();
        }
      };
    } else if (certificate instanceof byte[]) {
      final String sshKeyAlgorithm = myManager.getSSHKeyAlgorithm();
      command = new Runnable() {
        @Override
        public void run() {
          final ServerSSHDialog serverSSHDialog =
            new ServerSSHDialog(myProject, resultMayBeStored, url.toDecodedString(), sshKeyAlgorithm, (byte[])certificate);
          serverSSHDialog.show();
          result[0] = serverSSHDialog.getResult();
        }
      };
    } else {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Subversion: unknown certificate type from " + url.toDecodedString(),
                                                    MessageType.ERROR);
      return REJECTED;
    }

    showAndWait(command);
    return result[0];
  }

  private static void showAndWait(@NotNull Runnable command) {
    WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(command, ModalityState.any());
  }

  private void log(final String s) {
    LOG.debug(s);
  }

  public static class MyCallState {
    private final boolean myWasCalled;
    private boolean myWasCancelled;

    public MyCallState(boolean wasCalled, boolean wasCancelled) {
      myWasCalled = wasCalled;
      myWasCancelled = wasCancelled;
    }

    public boolean isWasCalled() {
      return myWasCalled;
    }

    public boolean isWasCancelled() {
      return myWasCancelled;
    }

    public void setWasCancelled(boolean wasCancelled) {
      myWasCancelled = wasCancelled;
    }
  }
}
