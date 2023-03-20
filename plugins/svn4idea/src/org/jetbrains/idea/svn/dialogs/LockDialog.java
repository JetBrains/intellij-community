// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowSettingOption;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author alex
 */
public class LockDialog extends OptionsDialog {
  private JTextArea myLockTextArea;
  private JCheckBox myForceCheckBox;

  @NonNls private static final String HELP_ID = "reference.VCS.subversion.lockFile";
  private final VcsShowSettingOption myOption;

  public LockDialog(Project project, boolean canBeParent, boolean multiple, @NonNls VcsShowSettingOption option) {
    super(project, canBeParent);
    myOption = option;

    setTitle(multiple ? SvnBundle.message("dialog.title.lock.files") : SvnBundle.message("dialog.title.lock.file"));
    setResizable(true);

    getHelpAction().setEnabled(true);
    init();
  }

  @Override
  protected String getHelpId() {
    return HELP_ID;
  }

  @Override
  public boolean shouldCloseOnCross() {
    return true;
  }

  public String getComment() {
    return myLockTextArea.getText();
  }

  public boolean isForce() {
    return myForceCheckBox.isSelected();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = JBUI.insets(2);
    gc.gridwidth = 1;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = 0;
    gc.weighty = 0;

    JLabel commentLabel = new JLabel(SvnBundle.message("label.lock.comment"));
    panel.add(commentLabel, gc);

    gc.gridy += 1;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;

    myLockTextArea = new JTextArea(7, 25);
    JScrollPane scrollPane =
      ScrollPaneFactory.createScrollPane(myLockTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setMinimumSize(scrollPane.getPreferredSize());
    panel.add(scrollPane, gc);
    commentLabel.setLabelFor(myLockTextArea);

    gc.gridy += 1;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.fill = GridBagConstraints.NONE;

    myForceCheckBox = new JCheckBox(SvnBundle.message("label.locl.steal.existing"));
    panel.add(myForceCheckBox, gc);

    gc.gridy += 1;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JSeparator(), gc);

    return panel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "svn.lockDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLockTextArea;
  }

  @Override
  protected boolean isToBeShown() {
    return true;
  }

  @Override
  protected void setToBeShown(final boolean value, final boolean onOk) {
    myOption.setValue(value);
  }

  @Override
  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }
}
