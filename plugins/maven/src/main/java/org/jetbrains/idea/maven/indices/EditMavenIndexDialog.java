// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;

import javax.swing.*;

public class EditMavenIndexDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myUrlField;

  public EditMavenIndexDialog() {
    this("");
  }

  public EditMavenIndexDialog(String url) {
    super(false);
    setTitle(MavenConfigurableBundle.message("maven.settings.index.edit.repository"));
    myUrlField.setText(url.isEmpty() ? "http://" : url);
    init();
  }

  @Override
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
