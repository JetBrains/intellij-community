// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.ui.ValidationInfo;

import javax.swing.*;

public class RenameTerminalSessionPanel extends JPanel {
  public JTextField myNameField;
  public JPanel myMainPanel;

  public RenameTerminalSessionPanel() {
    add(myMainPanel);
  }
}
