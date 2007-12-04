/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public abstract class AbstractSelectFilesDialog<T> extends DialogWrapper {
  protected ChangesTreeList<T> myFileList;
  protected JPanel myPanel;
  protected JCheckBox myDoNotShowCheckbox;
  protected final VcsShowConfirmationOption myConfirmationOption;

  public AbstractSelectFilesDialog(Project project, boolean canBeParent, final VcsShowConfirmationOption confirmationOption,
                                   final String prompt) {
    super(project, canBeParent);
    myConfirmationOption = confirmationOption;

    myPanel = new JPanel(new BorderLayout());

    if (prompt != null) {
      final JLabel label = new JLabel(prompt);
      label.setUI(new MultiLineLabelUI());
      myPanel.add(label, BorderLayout.NORTH);
    }

    myDoNotShowCheckbox = new JCheckBox(CommonBundle.message("dialog.options.do.not.show"));
    myPanel.add(myDoNotShowCheckbox, BorderLayout.SOUTH);
  }

  @Override
  protected JComponent createNorthPanel() {
    DefaultActionGroup group = new DefaultActionGroup();
    final AnAction[] actions = myFileList.getTreeActions();
    for(AnAction action: actions) {
      group.add(action);
    }
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  protected void doOKAction() {
    if (myDoNotShowCheckbox.isSelected()) {
      myConfirmationOption.setValue(VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFileList;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
