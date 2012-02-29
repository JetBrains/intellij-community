/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.clientProperties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class AddClientPropertyDialog extends DialogWrapper {
  private JTextField myPropertyNameTextField;
  private JRadioButton myStringRadioButton;
  private JRadioButton myIntegerRadioButton;
  private JRadioButton myDoubleRadioButton;
  private JRadioButton myBooleanRadioButton;
  private JPanel myRootPanel;
  private JPanel myGroupPanel;

  public AddClientPropertyDialog(Project project) {
    super(project, false);
    init();
    setTitle(UIDesignerBundle.message("client.property.add.title"));
    myGroupPanel.setBorder(IdeBorderFactory.createTitledBorder(UIDesignerBundle.message("client.properties.type.header"),
                                                               true));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPropertyNameTextField;
  }

  public ClientPropertiesManager.ClientProperty getEnteredProperty() {
    String className;
    if (myStringRadioButton.isSelected()) {
      className = String.class.getName();
    }
    else if (myIntegerRadioButton.isSelected()) {
      className = Integer.class.getName();
    }
    else if (myDoubleRadioButton.isSelected()) {
      className = Double.class.getName();
    }
    else {
      className = Boolean.class.getName();
    }
    return new ClientPropertiesManager.ClientProperty(myPropertyNameTextField.getText(), className);
  }
}
