/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 14.11.2006
 * Time: 18:43:58
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.CommitSession;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diff.impl.patch.PatchBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.Icons;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;

public class CreatePatchCommitExecutor implements CommitExecutor, ProjectComponent {
  private Project myProject;
  private ChangeListManager myChangeListManager;

  public CreatePatchCommitExecutor(final Project project, final ChangeListManager changeListManager) {
    myProject = project;
    myChangeListManager = changeListManager;
  }

  @NotNull
  public Icon getActionIcon() {
    return Icons.TASK_ICON;
  }

  @Nls
  public String getActionText() {
    return VcsBundle.message("create.patch.commit.action.text");
  }

  @Nls
  public String getActionDescription() {
    return VcsBundle.message("create.patch.commit.action.description");
  }

  @NotNull
  public CommitSession createCommitSession() {
    return new CreatePatchCommitSession();
  }

  public void projectOpened() {
    myChangeListManager.registerCommitExecutor(this);
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "CreatePatchCommitExecutor";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private class CreatePatchCommitSession implements CommitSession {
    private CreatePatchConfigurationPanel myPanel = new CreatePatchConfigurationPanel();

    @Nullable
    public JComponent getAdditionalConfigurationUI() {
      return myPanel.getPanel();
    }

    public boolean canExecute(Collection<Change> changes, String commitMessage) {
      return true;
    }

    public void execute(Collection<Change> changes, String commitMessage) {
      try {
        Writer writer = new OutputStreamWriter(new FileOutputStream(myPanel.getFileName()));
        try {
          PatchBuilder.buildPatch(changes, myProject.getProjectFile().getParent().getPresentableUrl(), writer);
        }
        finally {
          writer.close();
        }
      }
      catch (IOException ex) {
        Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()), CommonBundle.getErrorTitle());
      }
    }

    public void executionCanceled() {
    }
  }
}