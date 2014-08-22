package org.jetbrains.plugins.coursecreator.ui;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CCNewProjectPanel {
  private JPanel myPanel;
  private JTextArea myDescription;
  private JTextField myName;
  private JTextField myAuthorField;


  public CCNewProjectPanel() {
    final String userName = System.getProperty("user.name");
    if (userName != null) {
      myAuthorField.setText(userName);
    }
  }

  public JPanel getMainPanel() {
    return myPanel;
  }

  @NotNull
  public String getName() {
    return StringUtil.notNullize(myName.getText());
  }

  @NotNull
  public String getDescription() {
    return StringUtil.notNullize(myDescription.getText());
  }

  @NotNull
  public String getAuthor() {
    return StringUtil.notNullize(myAuthorField.getText());
  }
}
