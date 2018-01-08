// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;

import javax.swing.*;

public class RelocateDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myFromURLTextField;
  private JTextField myToURLTextField;

  public RelocateDialog(@NotNull Project project, @NotNull Url url) {
    super(project, false);
    init();
    setTitle("Relocate Working Copy");
    myFromURLTextField.setText(url.toDecodedString());
    myToURLTextField.setText(url.toDecodedString());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @NotNull
  public String getBeforeURL() {
    return myFromURLTextField.getText();
  }

  @NotNull
  public String getAfterURL() {
    return myToURLTextField.getText();
  }
}
