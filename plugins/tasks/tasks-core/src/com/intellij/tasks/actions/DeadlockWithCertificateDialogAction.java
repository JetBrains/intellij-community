// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.tasks.TaskBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Mikhail Golubev
 */
public class DeadlockWithCertificateDialogAction extends AnAction {
  public static final String SELF_SIGNED_SERVER_URL = "https://self-signed.certificates-tests.labs.intellij.net";
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    ApplicationManager.getApplication().invokeLater(() -> {
      URL location = null;
      try {
        location = new URL(SELF_SIGNED_SERVER_URL);
      }
      catch (MalformedURLException ignored) {
      }
      // EDT will not be released for dialog until message dialog is shown. Meanwhile downloading of image by
      // ImageFetcher thread, started by MediaTracker, won't stop until certificate is accepted by user.
      Messages.showMessageDialog(e.getProject(),
                                 TaskBundle.message("dialog.message.this.dialog.may.not.be.shown"),
                                 TaskBundle.message("dialog.title.deadlocking.dialog"), new ImageIcon(location));
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
