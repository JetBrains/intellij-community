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
public class SelectFilteringAction extends AnAction implements CustomComponentAction {
  private CommittedChangesTreeBrowser myBrowser;
  private JPanel myPanel;

  public SelectFilteringAction(final CommittedChangesTreeBrowser browser) {
    myBrowser = browser;
  }

  public void actionPerformed(AnActionEvent e) {
  }

  public JComponent createCustomComponent(Presentation presentation) {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
      myPanel.add(new JLabel("Filter by"), BorderLayout.WEST);
      final JComboBox comboBox = new JComboBox(new Object[] { ChangeListFilteringStrategy.NONE, new UserFilteringStrategy()});
      comboBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myBrowser.setFilteringStrategy((ChangeListFilteringStrategy) comboBox.getSelectedItem());
        }
      });
      myPanel.add(comboBox, BorderLayout.CENTER);
    }
    return myPanel;
  }
}