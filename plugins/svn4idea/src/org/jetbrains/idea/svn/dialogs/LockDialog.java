package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 04.07.2005
 * Time: 14:39:24
 * To change this template use File | Settings | File Templates.
 */
public class LockDialog extends DialogWrapper {
  private JTextArea myLockTextArea;
  private JCheckBox myForceCheckBox;

  public LockDialog(Project project, boolean canBeParent) {
    super(project, canBeParent);
    setTitle("Lock Files");
    setResizable(true);

    init();
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

    JLabel commentLabel = new JLabel("&Lock comment:");
    panel.add(commentLabel, gc);

    gc.gridy += 1;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;

    myLockTextArea = new JTextArea(7, 25);
    JScrollPane scrollPane = new JScrollPane(myLockTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                                             JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setMinimumSize(scrollPane.getPreferredSize());
    panel.add(scrollPane, gc);
    DialogUtil.registerMnemonic(commentLabel, myLockTextArea);

    gc.gridy += 1;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.fill = GridBagConstraints.NONE;

    myForceCheckBox = new JCheckBox("&Steal existing lock");
    panel.add(myForceCheckBox, gc);
    DialogUtil.registerMnemonic(myForceCheckBox);

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
}
