/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.*;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionPanel extends JPanel {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myBrowseTargetFileButton;
  private JLabel myElementsToMove;

  public PyMoveClassOrFunctionPanel(String elementsToMoveText, String initialTargetFile) {
    super();
    this.setLayout(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);
    myElementsToMove.setText(elementsToMoveText);
    myBrowseTargetFileButton.setText(initialTargetFile);
  }

  public TextFieldWithBrowseButton getBrowseTargetFileButton() {
    return myBrowseTargetFileButton;
  }
}
