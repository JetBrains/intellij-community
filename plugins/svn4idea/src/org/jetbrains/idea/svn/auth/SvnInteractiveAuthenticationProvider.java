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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.SSLCredentialsDialog;
import org.jetbrains.idea.svn.dialogs.ServerSSLDialog;
import org.jetbrains.idea.svn.dialogs.SimpleCredentialsDialog;
import org.jetbrains.idea.svn.dialogs.UserNameCredentialsDialog;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.wc.ISVNHostOptions;

import java.io.File;
import java.security.cert.X509Certificate;

public class SvnInteractiveAuthenticationProvider implements AuthenticationProvider {
  private static final Logger LOG = Logger.getInstance(SvnInteractiveAuthenticationProvider.class);
  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;
  private static final ThreadLocal<MyCallState> myCallState = new ThreadLocal<>();
  private final SvnAuthenticationManager myManager;

  public SvnInteractiveAuthenticationProvider(@NotNull SvnVcs vcs, SvnAuthenticationManager manager) {
    myManager = manager;
    myVcs = vcs;
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

  @Override
  public SVNAuthentication requestClientAuthentication(final String kind,
                                                       final SVNURL url,
                                                       final String realm,
                                                       final boolean canCache) {
    final MyCallState callState = new MyCallState(true, false);
    myCallState.set(callState);
    // once we came here, we don't know _correct_ auth todo +-
    myVcs.getSvnConfiguration().clearCredentials(kind, realm);

    final SVNAuthentication[] result = new SVNAuthentication[1];
    Runnable command = null;

    final boolean authCredsOn = canCache && myManager.getHostOptionsProvider().getHostOptions(url).isAuthStorageEnabled();

    final String userName = myManager.getDefaultUsername();
    if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {// || ISVNAuthenticationManager.USERNAME.equals(kind)) {
      command = () -> {
        SimpleCredentialsDialog dialog = new SimpleCredentialsDialog(myProject);
        dialog.setup(realm, userName, authCredsOn);
        setTitle(dialog);
        if (dialog.showAndGet()) {
          result[0] = new SVNPasswordAuthentication(dialog.getUserName(), dialog.getPassword(), dialog.isSaveAllowed(), url, false);
        }
      };
    }
    else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return new SVNUserNameAuthentication(userName, false);
      }
      command = () -> {
        UserNameCredentialsDialog dialog = new UserNameCredentialsDialog(myProject);
        dialog.setup(realm, userName, authCredsOn);
        setTitle(dialog);
        if (dialog.showAndGet()) {
          result[0] = new SVNUserNameAuthentication(dialog.getUserName(), dialog.isSaveAllowed(), url, false);
        }
      };
    }
    else if (ISVNAuthenticationManager.SSL.equals(kind)) {
      command = () -> {
        final ISVNHostOptions options = myManager.getHostOptionsProvider().getHostOptions(url);
        final String file = options.getSSLClientCertFile();
        final SSLCredentialsDialog dialog = new SSLCredentialsDialog(myProject, realm, authCredsOn);
        if (!StringUtil.isEmptyOrSpaces(file)) {
          dialog.setFile(file);
        }
        setTitle(dialog);
        if (dialog.showAndGet()) {
          result[0] = new SVNSSLAuthentication(new File(dialog.getCertificatePath()), String.valueOf(dialog.getCertificatePassword()),
                                               dialog.getSaveAuth(), url, false);
        }
      };
    }

    if (command != null) {
      showAndWait(command);
      log("3 authentication result: " + result[0]);
    }

    final boolean wasCanceled = result[0] == null;
    callState.setWasCancelled(wasCanceled);
    return result[0];
  }

  private static void setTitle(@NotNull DialogWrapper dialog) {
    dialog.setTitle(SvnBundle.message("dialog.title.authentication.required"));
  }

  @Override
  public AcceptResult acceptServerAuthentication(final SVNURL url,
                                                 String realm,
                                                 final Object certificate,
                                                 final boolean canCache) {
    final int[] result = new int[1];
    Runnable command;
    if (certificate instanceof X509Certificate || certificate instanceof String) {
      command = () -> {
        ServerSSLDialog dialog = certificate instanceof X509Certificate
                                 ? new ServerSSLDialog(myProject, (X509Certificate)certificate, canCache)
                                 : new ServerSSLDialog(myProject, (String)certificate, canCache);
        dialog.show();
        result[0] = dialog.getResult();
      };
    } else {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Subversion: unknown certificate type from " + url.toDecodedString(),
                                                    MessageType.ERROR);
      return AcceptResult.REJECTED;
    }

    showAndWait(command);
    return AcceptResult.from(result[0]);
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
