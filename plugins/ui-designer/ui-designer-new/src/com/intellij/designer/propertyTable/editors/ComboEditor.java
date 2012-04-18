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

import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.openapi.ui.ComboBox;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author Alexander Lobas
 */
public abstract class ComboEditor extends PropertyEditor {
  protected final ComboBox myCombo;

  public ComboEditor() {
    myCombo = new ComboBox(-1);
    myCombo.setBorder(null);
    addEditorSupport(this, myCombo);
  }

  public static void addEditorSupport(PropertyEditor editor, JComboBox myCombo) {
    ComboListeners listeners = new ComboListeners(editor);
    myCombo.addPopupMenuListener(listeners);
    myCombo.registerKeyboardAction(listeners, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCombo);
    ListCellRenderer renderer = myCombo.getRenderer();
    if (renderer instanceof JComponent) {
      SwingUtilities.updateComponentTreeUI((JComponent)renderer);
    }
  }

  private static class ComboListeners implements PopupMenuListener, ActionListener {
    private final PropertyEditor myEditor;
    private boolean myCancelled;

    public ComboListeners(PropertyEditor editor) {
      myEditor = editor;
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      myCancelled = false;
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      if (!myCancelled) {
        myEditor.fireValueCommitted(true, true);
      }
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
      myCancelled = true;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myCancelled = true;
      myEditor.fireEditingCancelled();
    }
  }
}