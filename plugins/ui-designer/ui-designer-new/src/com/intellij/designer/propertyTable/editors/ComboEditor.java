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
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * @author Alexander Lobas
 */
public class ComboEditor extends PropertyEditor {
  protected final ComboBox myCombo;

  public ComboEditor() {
    myCombo = new ComboBox(-1);
    myCombo.setBorder(null);
    myCombo.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        // TODO: Auto-generated method stub
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        // TODO: Auto-generated method stub
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        // TODO: Auto-generated method stub
      }
    });
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCombo);
    ListCellRenderer renderer = myCombo.getRenderer();
    if (renderer instanceof JComponent) {
      SwingUtilities.updateComponentTreeUI((JComponent)renderer);
    }
  }

  @Override
  public Object getValue() throws Exception {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable RadComponent component, Object value) {
    return null;  // TODO: Auto-generated method stub
  }
}