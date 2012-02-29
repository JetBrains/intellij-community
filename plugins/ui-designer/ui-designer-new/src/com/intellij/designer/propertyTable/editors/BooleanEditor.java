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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class BooleanEditor extends PropertyEditor {
  private final JCheckBox myCheckBox;
  private boolean myInsideChange;

  public BooleanEditor() {
    myCheckBox = new JCheckBox();
    myCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (!myInsideChange) {
          fireValueCommitted(true, false);
        }
      }
    });
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCheckBox);
  }

  public Object getValue() throws Exception {
    return Boolean.valueOf(myCheckBox.isSelected());
  }

  @NotNull
  public JComponent getComponent(@Nullable RadComponent component, Object value) {
    try {
      myInsideChange = true;
      myCheckBox.setBackground(UIUtil.getTableBackground());
      myCheckBox.setSelected(value != null && (Boolean)value);
      return myCheckBox;
    }
    finally {
      myInsideChange = false;
    }
  }
}