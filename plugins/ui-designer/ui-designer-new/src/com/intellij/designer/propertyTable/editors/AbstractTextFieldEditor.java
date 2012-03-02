/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable.editors;

import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.PropertyEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractTextFieldEditor extends PropertyEditor {
  protected final JTextField myTextField = new JTextField();

  protected AbstractTextFieldEditor() {
    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        fireValueCommitted(true, false);
      }
    });
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable RadComponent component, Object value) {
    setEditorValue(component, value);
    myTextField.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    return myTextField;
  }

  protected void setEditorValue(@Nullable RadComponent component, Object value) {
    myTextField.setText(value == null ? "" : value.toString());
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }
}