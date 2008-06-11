package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class EditMavenRepositoryDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myIdField;
  private JTextField myUrlField;

  public EditMavenRepositoryDialog() {
    this("", "");
  }

  public EditMavenRepositoryDialog(String id, String url) {
    super(false);
    setTitle("Maven Repository");
    myIdField.setText(id);
    myUrlField.setText(url);
    init();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getId() {
    return myIdField.getText();
  }

  public String getUrl() {
    return myUrlField.getText();
  }
}
