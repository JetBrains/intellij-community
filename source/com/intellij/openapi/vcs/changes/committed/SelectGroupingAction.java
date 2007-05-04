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
public class SelectGroupingAction extends AnAction implements CustomComponentAction {
  private CommittedChangesTreeBrowser myBrowser;
  private JPanel myPanel;

  public SelectGroupingAction(final CommittedChangesTreeBrowser browser) {
    myBrowser = browser;
  }

  public void actionPerformed(AnActionEvent e) {
  }

  public JComponent createCustomComponent(Presentation presentation) {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());
      myPanel.add(new JLabel("Group by"), BorderLayout.WEST);
      final JComboBox comboBox = new JComboBox(new Object[] { ChangeListGroupingStrategy.DATE, ChangeListGroupingStrategy.USER });
      comboBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myBrowser.setGroupingStrategy((ChangeListGroupingStrategy) comboBox.getSelectedItem());
        }
      });
      myPanel.add(comboBox, BorderLayout.CENTER);
    }
    return myPanel;
  }
}