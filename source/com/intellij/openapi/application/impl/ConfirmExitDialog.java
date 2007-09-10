/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.ui.OptionsDialog;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 2, 2005
 */
public class ConfirmExitDialog extends OptionsDialog {


  public ConfirmExitDialog() {
    super(false);
    setTitle(ApplicationBundle.message("exit.confirm.title"));
    init();
  }

  protected Action[] createActions() {
    setOKButtonText(CommonBundle.getYesButtonText());
    setCancelButtonText(CommonBundle.getNoButtonText());
    return new Action[] {getOKAction(), getCancelAction()};
  }

  protected boolean isToBeShown() {
    return GeneralSettings.getInstance().isConfirmExit() || hasTasks();
  }

  private boolean hasTasks() {
    return ProgressManager.getInstance().hasProgressIndicator();
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    GeneralSettings.getInstance().setConfirmExit(value);
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return !hasTasks();
  }

  protected boolean canBeHidden() {
    return !hasTasks();
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());

    final String message = ApplicationBundle.message(hasTasks() ? "exit.confirm.prompt.tasks": "exit.confirm.prompt", ApplicationNamesInfo.getInstance().getFullProductName());

    final JLabel label = new JLabel(message);
    label.setIconTextGap(10);
    label.setIcon(Messages.getQuestionIcon());
    panel.add(label, BorderLayout.CENTER);
    panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
    return panel;
  }
}
