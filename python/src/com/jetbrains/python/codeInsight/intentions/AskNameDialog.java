package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.jetbrains.python.PyNames;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AskNameDialog extends DialogWrapper {
  private JPanel contentPane;
  private JTextField myAliasTextField;

  protected AskNameDialog(Project project) {
    super(project);
    myAliasTextField.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent actionEvent) {
          AskNameDialog.this.setOKActionEnabled(PyNames.isIdentifier(myAliasTextField.getText()));
        }
      }
    );
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAliasTextField;
  }

  public String getAlias() {
    return myAliasTextField.getText();
  }
}
