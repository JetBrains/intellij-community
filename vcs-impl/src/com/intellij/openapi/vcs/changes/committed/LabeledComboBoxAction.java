package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author yole
 */
public abstract class LabeledComboBoxAction extends AnAction implements CustomComponentAction {
  private String myLabel;
  private JPanel myPanel;

  protected LabeledComboBoxAction(String label) {
    myLabel = label;
  }

  public void actionPerformed(AnActionEvent e) {
  }

  public JComponent createCustomComponent(Presentation presentation) {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
      myPanel.add(new JLabel(myLabel), BorderLayout.WEST);
      final JComboBox comboBox = new JComboBox(createModel());
      comboBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          selectionChanged(comboBox.getSelectedItem());
        }
      });
      myPanel.add(comboBox, BorderLayout.CENTER);
    }
    return myPanel;
  }

  protected abstract void selectionChanged(Object selection);

  protected abstract ComboBoxModel createModel();
}