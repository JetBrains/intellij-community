// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.SSLCredentialsDialog;
import org.jetbrains.idea.svn.dialogs.ServerSSLDialog;
import org.jetbrains.idea.svn.dialogs.SimpleCredentialsDialog;

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
  public AuthenticationData requestClientAuthentication(final String kind,
                                                        final Url url,
                                                        final String realm,
                                                        final boolean canCache) {
    final MyCallState callState = new MyCallState(true, false);
    myCallState.set(callState);
    // once we came here, we don't know _correct_ auth todo +-
    myVcs.getSvnConfiguration().clearCredentials(kind, realm);

    final AuthenticationData[] result = new AuthenticationData[1];
    Runnable command = null;

    final boolean authCredsOn = canCache && myManager.getHostOptions(url).isAuthStorageEnabled();

    final String userName = myManager.getDefaultUsername();
    if (SvnAuthenticationManager.PASSWORD.equals(kind)) {
      command = () -> {
        SimpleCredentialsDialog dialog = new SimpleCredentialsDialog(myProject);
        dialog.setup(realm, userName, authCredsOn);
        setTitle(dialog);
        if (dialog.showAndGet()) {
          result[0] = new PasswordAuthenticationData(dialog.getUserName(), dialog.getPassword(), dialog.isSaveAllowed());
        }
      };
    }
    else if (SvnAuthenticationManager.SSL.equals(kind)) {
      command = () -> {
        SvnAuthenticationManager.HostOptions options = myManager.getHostOptions(url);
        final String file = options.getSSLClientCertFile();
        final SSLCredentialsDialog dialog = new SSLCredentialsDialog(myProject, realm, authCredsOn);
        if (!StringUtil.isEmptyOrSpaces(file)) {
          dialog.setFile(file);
        }
        setTitle(dialog);
        if (dialog.showAndGet()) {
          result[0] = new CertificateAuthenticationData(dialog.getCertificatePath(), dialog.getCertificatePassword(), dialog.getSaveAuth());
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
  public AcceptResult acceptServerAuthentication(final Url url,
                                                 String realm,
                                                 final Object certificate,
                                                 final boolean canCache) {
    Ref<AcceptResult> result = Ref.create(AcceptResult.REJECTED);
    Runnable command;
    if (certificate instanceof X509Certificate || certificate instanceof String) {
      command = () -> {
        ServerSSLDialog dialog = certificate instanceof X509Certificate
                                 ? new ServerSSLDialog(myProject, (X509Certificate)certificate, canCache)
                                 : new ServerSSLDialog(myProject, (String)certificate, canCache);
        dialog.show();
        result.set(dialog.getResult());
      };
    } else {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Subversion: unknown certificate type from " + url.toDecodedString(),
                                                    MessageType.ERROR);
      return AcceptResult.REJECTED;
    }

    showAndWait(command);
    return result.get();
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
