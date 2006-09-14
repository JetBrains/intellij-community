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
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author max
 */
public class ChangeListViewerDialog extends DialogWrapper {
  private final Project myProject;
  private final CommittedChangeList myChangeList;
  private ChangesBrowser myChangesBrowser;

  public ChangeListViewerDialog(Project project, CommittedChangeList changeList) {
    super(project, true);
    myProject = project;
    myChangeList = changeList;
    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setModal(false);

    init();
  }

  protected String getDimensionServiceKey() {
    return "VCS.ChangeListViewerDialog";
  }

  protected JComponent createCenterPanel() {
    myChangesBrowser = new ChangesBrowser(myProject, Collections.singletonList(myChangeList),
                                          new ArrayList<Change>(myChangeList.getChanges()),
                                          myChangeList, false, false);
    return myChangesBrowser;
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