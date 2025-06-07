// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;

import javax.swing.*;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class RelocateDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myFromURLTextField;
  private JTextField myToURLTextField;

  public RelocateDialog(@NotNull Project project, @NotNull Url url) {
    super(project, false);
    init();
    setTitle(message("dialog.title.relocate.working.copy"));
    myFromURLTextField.setText(url.toDecodedString());
    myToURLTextField.setText(url.toDecodedString());
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myMainPanel;
  }

  public @NotNull String getBeforeURL() {
    return myFromURLTextField.getText();
  }

  public @NotNull String getAfterURL() {
    return myToURLTextField.getText();
  }
}
