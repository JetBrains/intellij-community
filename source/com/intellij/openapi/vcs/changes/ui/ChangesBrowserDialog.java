/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;

/**
 * @author max
 */
public class ChangesBrowserDialog extends DialogWrapper {
  private final Project myProject;
  private final ListTableModel<CommittedChangeList> myChanges;
  private final Mode myMode;
  private CommittedChangesProvider myCommittedChangesProvider;
  private CommittedChangesBrowser myCommittedChangesBrowser;

  public enum Mode { Simple, Browse, Choose }

  public ChangesBrowserDialog(Project project, ListTableModel<CommittedChangeList> changes, final Mode mode) {
    super(project, true);
    myProject = project;
    myChanges = changes;
    myMode = mode;
    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    if (mode != Mode.Choose) {
      setModal(false);
    }

    init();
  }

  public void setCommittedChangesProvider(final CommittedChangesProvider committedChangesProvider) {
    myCommittedChangesProvider = committedChangesProvider;
  }

  protected String getDimensionServiceKey() {
    return "VCS.ChangesBrowserDialog";
  }

  protected JComponent createCenterPanel() {
    myCommittedChangesBrowser = new CommittedChangesBrowser(myProject, myChanges);
    return myCommittedChangesBrowser;
  }

  @Override
  protected void dispose() {
    super.dispose();
    myCommittedChangesBrowser.dispose();
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    if (myMode == Mode.Browse) {
      getOKAction().putValue(Action.NAME, VcsBundle.message("button.search.again"));
    }
  }

  @Override
  protected Action[] createActions() {
    if (myMode == Mode.Simple) {
      return new Action[] { getCancelAction() };
    }
    return super.createActions();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    /*
    if (myMode == Mode.Browse && myCommittedChangesProvider != null) {
      AbstractVcsHelper.getInstance(myProject).showChangesBrowser(myCommittedChangesProvider, root, getTitle());
    }
    */
  }

  public CommittedChangeList getSelectedChangeList() {
    return myCommittedChangesBrowser.getSelectedChangeList();
  }
}
