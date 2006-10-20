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
import com.intellij.ui.IdeBorderFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.awt.*;

/**
 * @author max
 */
public class ChangeListViewerDialog extends DialogWrapper {
  private final Project myProject;
  private final CommittedChangeList myChangeList;
  private ChangesBrowser myChangesBrowser;
  private final JTextArea myCommitMessageArea;

  public ChangeListViewerDialog(Project project, CommittedChangeList changeList) {
    super(project, true);
    myProject = project;
    myChangeList = changeList;

    myCommitMessageArea = new JTextArea();
    myCommitMessageArea.setRows(3);
    myCommitMessageArea.setWrapStyleWord(true);
    myCommitMessageArea.setLineWrap(true);
    myCommitMessageArea.setEditable(false);
    myCommitMessageArea.setText(changeList.getComment());

    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setModal(false);

    init();
  }

  protected String getDimensionServiceKey() {
    return "VCS.ChangeListViewerDialog";
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    myChangesBrowser = new ChangesBrowser(myProject, Collections.singletonList(myChangeList),
                                          new ArrayList<Change>(myChangeList.getChanges()),
                                          myChangeList, false, false);
    panel.add(myChangesBrowser, BorderLayout.CENTER);

    JPanel commitPanel = new JPanel(new BorderLayout());
    commitPanel.add(new JScrollPane(myCommitMessageArea), BorderLayout.CENTER);
    commitPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("label.commit.comment")));

    panel.add(commitPanel, BorderLayout.SOUTH);

    return panel;
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
}