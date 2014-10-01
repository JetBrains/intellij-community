/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.packaging.PyPackageManagerUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author vlan
 */
public class PyPackageNotificationDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JBLabel myErrorMessage;
  private JTextArea myCommandOutput;
  private JPanel myCommandOutputPanel;
  private JPanel myCommandPanel;
  private JTextPane myCommand;
  private JPanel mySolutionPanel;
  private JTextPane mySolution;

  public PyPackageNotificationDialog(@NotNull String title, @NotNull PyPackageManagerUI.ExecutionFailure failure) {
    super(false);
    init();
    setResizable(false);
    setTitle(title);
    final String command = failure.getCommand();
    final String output = failure.getOutput();
    final String message = failure.getMessage();
    final String solution = failure.getSolution();

    myCommandPanel.setVisible(command != null);
    myCommandOutputPanel.setVisible(output != null);
    mySolutionPanel.setVisible(solution != null);

    myErrorMessage.setText(message);
    if (command != null) {
      myCommand.setText(command);
    }
    if (output != null) {
      myCommandOutput.setText(output);
    }
    if (solution != null) {
      mySolution.setText(solution);
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
