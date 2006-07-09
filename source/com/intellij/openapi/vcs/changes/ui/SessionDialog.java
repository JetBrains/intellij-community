/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitSession;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SessionDialog extends DialogWrapper {
  private final CommitSession mySession;
  private final List<Change> myChanges;

  private final Alarm myOKButtonUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final String myCommitMessage;

  private final JPanel myCenterPanel = new JPanel(new BorderLayout());

  public SessionDialog(String title, Project project,
                       CommitSession session, List<Change> changes,
                       String commitMessage) {
    super(project, true);
    mySession = session;
    myChanges = changes;
    myCommitMessage = commitMessage;
    setTitle(title);
    updateButtons();
    init();
  }


  @Nullable
  protected JComponent createCenterPanel() {
    myCenterPanel.add(mySession.getAdditionalConfigurationUI(), BorderLayout.CENTER);
    return myCenterPanel;
  }

  private void updateButtons() {
    setOKActionEnabled(mySession.canExecute(myChanges, myCommitMessage));
    myOKButtonUpdateAlarm.cancelAllRequests();
    myOKButtonUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        updateButtons();
      }
    }, 300, ModalityState.stateForComponent(myCenterPanel));
  }

  protected void dispose() {
    super.dispose();
    myOKButtonUpdateAlarm.cancelAllRequests();
  }
}
