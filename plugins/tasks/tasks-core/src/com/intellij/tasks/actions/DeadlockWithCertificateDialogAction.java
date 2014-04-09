package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Mikhail Golubev
 */
public class DeadlockWithCertificateDialogAction extends AnAction {
  public static final String SELF_SIGNED_SERVER_URL = "https://self-signed.certificates-tests.labs.intellij.net";
  @Override
  public void actionPerformed(final AnActionEvent e) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        URL location = null;
        try {
          location = new URL(SELF_SIGNED_SERVER_URL);
        }
        catch (MalformedURLException ignored) {
        }
        // EDT will not be released for dialog until message dialog is shown. Meanwhile downloading of image by
        // ImageFetcher thread, started by MediaTracker, won't stop until certificate is accepted by user.
        Messages.showMessageDialog(e.getProject(),
                                   "This dialog may not be shown due to deadlock caused by MediaTracker and CertificateManager. " +
                                   "Fortunately it didn't happen." ,
                                   "Deadlocking Dialog", new ImageIcon(location));
      }
    });
  }
}
