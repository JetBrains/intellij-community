/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.util.ui.OptionsDialog;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 2, 2005
 */
public class ChangelistMoveOfferDialog extends OptionsDialog {
  private final VcsConfiguration myConfig;

  public ChangelistMoveOfferDialog(VcsConfiguration config) {
    super(false);
    myConfig = config;
    setTitle(VcsBundle.message("changes.commit.partial.offer.to.move.title"));
    init();
  }

  protected Action[] createActions() {
    setOKButtonText(CommonBundle.getYesButtonText());
    setCancelButtonText(CommonBundle.getNoButtonText());
    return new Action[] {getOKAction(), getCancelAction()};
  }

  protected boolean isToBeShown() {
    return myConfig.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT;
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    myConfig.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = value;
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }

  
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(VcsBundle.message("changes.commit.partial.offer.to.move.text"));
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    label.setIcon(Messages.getQuestionIcon());
    panel.add(label, BorderLayout.CENTER);
    panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
    return panel;
  }
}
