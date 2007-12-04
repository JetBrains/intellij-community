/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 30.11.2006
 * Time: 19:39:02
 */
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class CommittedChangesFilterDialog extends DialogWrapper {
  private final ChangesBrowserSettingsEditor myPanel;
  private ChangeBrowserSettings mySettings;
  private JLabel myErrorLabel = new JLabel();
  private Alarm myValidateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Runnable myValidateRunnable = new Runnable() {
    public void run() {
      validateInput();
      myValidateAlarm.addRequest(myValidateRunnable, 500, ModalityState.stateForComponent(myPanel.getComponent()));
    }
  };

  public CommittedChangesFilterDialog(Project project, ChangesBrowserSettingsEditor panel, ChangeBrowserSettings settings) {
    super(project, false);
    myPanel = panel;
    //noinspection unchecked
    myPanel.setSettings(settings);
    setTitle(VcsBundle.message("browse.changes.filter.title"));
    init();
    myErrorLabel.setForeground(Color.red);
    validateInput();
    myValidateAlarm.addRequest(myValidateRunnable, 500, ModalityState.stateForComponent(myPanel.getComponent()));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myPanel.getComponent(), BorderLayout.CENTER);
    panel.add(myErrorLabel, BorderLayout.SOUTH);
    return panel;
  }

  private void validateInput() {
    String error = myPanel.validateInput();
    setOKActionEnabled(error == null);
    myErrorLabel.setText(error == null ? " " : error);
  }

  @Override
  protected void doOKAction() {
    validateInput();
    if (isOKActionEnabled()) {
      myValidateAlarm.cancelAllRequests();
      mySettings = myPanel.getSettings();
      super.doOKAction();
    }
  }

  public ChangeBrowserSettings getSettings() {
    return mySettings;
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "AbstractVcsHelper.FilterDialog." + myPanel.getDimensionServiceKey();
  }
}