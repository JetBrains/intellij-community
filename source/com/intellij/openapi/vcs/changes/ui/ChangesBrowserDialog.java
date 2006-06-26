/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.util.List;

/**
 * @author max
 */
public class ChangesBrowserDialog extends DialogWrapper {
  private final Project myProject;
  private final List<CommittedChangeList> myChanges;

  public ChangesBrowserDialog(Project project, List<CommittedChangeList> changes) {
    super(project, true);
    myProject = project;
    myChanges = changes;
    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setModal(false);

    init();
  }

  protected String getDimensionServiceKey() {
    return "VCS.ChangesBrowserDialog";
  }

  protected JComponent createCenterPanel() {
    return new CommittedChangesBrowser(myProject, myChanges);
  }

  @Override
  protected Action[] createActions() {
    return new Action[] { getCancelAction() };
  }
}
