/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import java.awt.*;

/**
 * @author alex
 */
public class LockDialog extends OptionsDialog {
  private JTextArea myLockTextArea;
  private JCheckBox myForceCheckBox;

  @NonNls private static final String HELP_ID = "reference.VCS.subversion.lockFile";

  public LockDialog(Project project, boolean canBeParent, boolean multiple) {
    super(project, canBeParent);
    setTitle(multiple ? SvnBundle.message("dialog.title.lock.files") : SvnBundle.message("dialog.title.lock.file"));
    setResizable(true);

    getHelpAction().setEnabled(true);
    init();

  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public String getComment() {
    return myLockTextArea.getText();
  }

  public boolean isForce() {
    return myForceCheckBox.isSelected();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
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

  protected String getDimensionServiceKey() {
    return "svn.lockDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myLockTextArea;
  }

  protected boolean isToBeShown() {
    return true;
  }

  protected void setToBeShown(final boolean value, final boolean onOk) {
    SvnVcs.getInstance(myProject).getCheckoutOptions().setValue(value);
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }
}
