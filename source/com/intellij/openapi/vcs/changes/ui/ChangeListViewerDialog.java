/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.07.2006
 * Time: 21:07:50
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.ui.SeparatorFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * @author max
 */
public class ChangeListViewerDialog extends DialogWrapper {
  private Project myProject;
  private CommittedChangeList myChangeList;
  private ChangesBrowser myChangesBrowser;
  private JTextArea myCommitMessageArea;

  public ChangeListViewerDialog(Project project, CommittedChangeList changeList) {
    super(project, true);
    initCommitMessageArea(changeList);
    initDialog(project, changeList);
  }

  public ChangeListViewerDialog(Component parent, Project project, Collection<Change> changes) {
    super(parent, true);
    initDialog(project, new CommittedChangeListImpl("", "", "", -1, new Date(0), changes));
  }

  private void initDialog(final Project project, final CommittedChangeList changeList) {
    myProject = project;
    myChangeList = changeList;

    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.message("close.action.name"));
    setModal(false);

    init();
  }

  private void initCommitMessageArea(final CommittedChangeList changeList) {
    myCommitMessageArea = new JTextArea();
    myCommitMessageArea.setRows(3);
    myCommitMessageArea.setWrapStyleWord(true);
    myCommitMessageArea.setLineWrap(true);
    myCommitMessageArea.setEditable(false);
    myCommitMessageArea.setText(changeList.getComment());
  }


  protected String getDimensionServiceKey() {
    return "VCS.ChangeListViewerDialog";
  }

  protected JComponent createCenterPanel() {
    final JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());
    myChangesBrowser = new RepositoryChangesBrowser(myProject, Collections.singletonList(myChangeList),
                                                    new ArrayList<Change>(myChangeList.getChanges()),
                                                    myChangeList);
    mainPanel.add(myChangesBrowser, BorderLayout.CENTER);

    if (myCommitMessageArea != null) {
      JPanel commitPanel = new JPanel(new BorderLayout());
      JComponent separator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myCommitMessageArea);
      commitPanel.add(separator, BorderLayout.NORTH);
      commitPanel.add(new JScrollPane(myCommitMessageArea), BorderLayout.CENTER);

      mainPanel.add(commitPanel, BorderLayout.SOUTH);
    }

    return mainPanel;
  }

  @Override
  protected void dispose() {
    myChangesBrowser.dispose();
    super.dispose();
  }

  @Override
  protected Action[] createActions() {
    return new Action[] { getCancelAction() };
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myChangesBrowser;
  }
}
