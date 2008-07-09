package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFileManager;

import javax.swing.*;

public class EditMavenIndexDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myUrlField;

  public EditMavenIndexDialog() {
    this("");
  }

  public EditMavenIndexDialog(String url) {
    super(false);
    setTitle("Edit Maven Repository");
    myUrlField.setText(url.length() == 0 ? "http://" : url);
    init();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getUrl() {
    String result = myUrlField.getText();
    if (VirtualFileManager.extractProtocol(result) == null) {
      result = "http://" + result;
    }
    return result;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUrlField;
  }
}
