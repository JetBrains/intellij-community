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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.GuiUtils;
import com.intellij.util.SystemProperties;
import org.jetbrains.idea.svn.SvnAuthenticationManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

public class SvnInteractiveAuthenticationProvider implements ISVNAuthenticationProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.dialogs.SvnInteractiveAuthenticationProvider");
  private final Project myProject;
  private static final ThreadLocal<MyCallState> myCallState = new ThreadLocal<MyCallState>();
  private final SvnVcs myVcs;
  private final SvnAuthenticationManager myManager;

  public SvnInteractiveAuthenticationProvider(final SvnVcs vcs, SvnAuthenticationManager manager) {
    myVcs = vcs;
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
                                                       SVNErrorMessage errorMessage,
                                                       final SVNAuthentication previousAuth,
                                                       final boolean authMayBeStored) {
    final MyCallState callState = new MyCallState(true, false);
    myCallState.set(callState);
    // once we came here, we don't know _correct_ auth todo +-
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    configuration.clearCredentials(kind, realm);

    final SVNAuthentication[] result = new SVNAuthentication[1];
    Runnable command = null;

    final boolean authCredsOn = authMayBeStored && myManager.authCredsOn(url);

    final String userName =
      previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : SystemProperties.getUserName();
    if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {// || ISVNAuthenticationManager.USERNAME.equals(kind)) {
      command = new Runnable() {
        public void run() {
          SimpleCredentialsDialog dialog = new SimpleCredentialsDialog(myProject);
          dialog.setup(realm, userName, authCredsOn);
          if (previousAuth == null) {
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required"));
          }
          else {
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required.was.failed"));
          }
          dialog.show();
          if (dialog.isOK()) {
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
          if (previousAuth == null) {                                                               
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required"));
          }
          else {
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required.was.failed"));
          }
          dialog.show();
          if (dialog.isOK()) {
            result[0] = new SVNUserNameAuthentication(dialog.getUserName(), dialog.isSaveAllowed(), url, false);
          }
        }
      };
    }
    else if (ISVNAuthenticationManager.SSH.equals(kind)) {
      command = new Runnable() {
        public void run() {
          SSHCredentialsDialog dialog = new SSHCredentialsDialog(myProject, realm, userName, authCredsOn, url.getPort());
          if (previousAuth == null) {
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required"));
          }
          else {
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required.was.failed"));
          }
          dialog.show();
          if (dialog.isOK()) {
            int port = dialog.getPortNumber();
            if (dialog.getKeyFile() != null && dialog.getKeyFile().trim().length() > 0) {
              String passphrase = dialog.getPassphrase();
              if (passphrase != null && passphrase.length() == 0) {
                passphrase = null;
              }
              result[0] =
                new SVNSSHAuthentication(dialog.getUserName(), new File(dialog.getKeyFile()), passphrase, port, dialog.isSaveAllowed(),
                                         url, false);
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
          final SSLCredentialsDialog dialog = new SSLCredentialsDialog(myProject, realm, authCredsOn);
          if (previousAuth == null) {
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required"));
          }
          else {
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required.was.failed"));
          }
          dialog.show();
          if (dialog.isOK()) {
            result[0] = new SVNSSLAuthentication(new File(dialog.getCertificatePath()), String.valueOf(dialog.getCertificatePassword()),
                                                 dialog.getSaveAuth(), url, false);
          }
        }
      };
    }

    if (command != null) {
      try {
        GuiUtils.runOrInvokeAndWait(command);
      }
      catch (InterruptedException e) {
        //
      }
      catch (InvocationTargetException e) {
        //
      }
      log("3 authentication result: " + result[0]);
    }

    final boolean wasCanceled = result[0] == null;
    callState.setWasCancelled(wasCanceled);
    return result[0];
  }

  public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
    // do-not-need-it-here
    return ISVNAuthenticationProvider.REJECTED;
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
