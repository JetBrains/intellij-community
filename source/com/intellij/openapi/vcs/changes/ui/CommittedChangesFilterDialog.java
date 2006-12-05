/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.11.2006
 * Time: 19:39:02
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CommittedChangesFilterDialog extends DialogWrapper {
  private final RefreshableOnComponent myPanel;

  public CommittedChangesFilterDialog(Project project, RefreshableOnComponent panel) {
    super(project, false);
    myPanel = panel;
    myPanel.restoreState();
    setTitle(VcsBundle.message("browse.changes.filter.title"));
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel.getComponent();
  }

  @Override
  protected void doOKAction() {
    myPanel.saveState();
    super.doOKAction();
  }
}