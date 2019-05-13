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
package com.jetbrains.python.configuration;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class AddVEnvOptionsDialog extends DialogWrapper {
  private JBCheckBox myMakeAvailableToAllJBCheckBox;
  private JPanel myMainPanel;

  public AddVEnvOptionsDialog(Component parent) {
    super(parent, false);
    init();
    setTitle("Add Virtualenv");
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public boolean makeAvailableToAll() {
    return myMakeAvailableToAllJBCheckBox.isSelected();
  }
}
