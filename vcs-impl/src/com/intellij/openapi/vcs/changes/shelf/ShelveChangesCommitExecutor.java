/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 13:40:27
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.CommitSession;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class ShelveChangesCommitExecutor implements CommitExecutor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesCommitExecutor");

  private final Project myProject;

  public ShelveChangesCommitExecutor(final Project project) {
    myProject = project;
  }

  @NotNull
  public Icon getActionIcon() {
    return Icons.TASK_ICON;
  }

  @Nls
  public String getActionText() {
    return VcsBundle.message("shelve.changes.action");
  }

  @Nls
  public String getActionDescription() {
    return VcsBundle.message("shelve.changes.action");
  }

  @NotNull
  public CommitSession createCommitSession() {
    return new ShelveChangesCommitSession();
  }

  private class ShelveChangesCommitSession implements CommitSession {

    @Nullable
    public JComponent getAdditionalConfigurationUI() {
      return null;
    }

    @Nullable
    public JComponent getAdditionalConfigurationUI(final Collection<Change> changes, final String commitMessage) {
      return null;
    }

    public boolean canExecute(Collection<Change> changes, String commitMessage) {
      return changes.size() > 0;
    }

    public void execute(Collection<Change> changes, String commitMessage) {
      if (changes.size() > 0 && !ChangesUtil.hasFileChanges(changes)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(myProject, VcsBundle.message("shelve.changes.only.directories"), VcsBundle.message("shelve.changes.action"));
          }
        });
        return;
      }
      try {
        final ShelvedChangeList list = ShelveChangesManager.getInstance(myProject).shelveChanges(changes, commitMessage);
        ShelvedChangesViewManager.getInstance(myProject).activateView(list);
      }
      catch (final Exception ex) {
        LOG.info(ex);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()), CommonBundle.getErrorTitle());
          }
        }, ModalityState.NON_MODAL);
      }
    }

    public void executionCanceled() {
    }
  }
}