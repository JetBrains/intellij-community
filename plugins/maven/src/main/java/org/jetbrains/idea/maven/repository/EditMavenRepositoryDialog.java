package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class EditMavenRepositoryDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myIdField;
  private JTextField myUrlField;

  public EditMavenRepositoryDialog(Project p) {
    this(p, "", "");
  }

  public EditMavenRepositoryDialog(Project p, String id, String url) {
    super(p, false);
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
