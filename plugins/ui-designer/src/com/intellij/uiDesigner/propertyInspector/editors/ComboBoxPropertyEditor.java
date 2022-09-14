// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;

public abstract class ComboBoxPropertyEditor<V> extends PropertyEditor<V> {
  protected final ComboBox<V> myCbx;

  public ComboBoxPropertyEditor() {
    myCbx = new ComboBox<>(-1);
    myCbx.setBorder(null);
    myCbx.addPopupMenuListener(new MyPopupMenuListener());
  }

  @Override
  public final void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCbx);
    final ListCellRenderer renderer = myCbx.getRenderer();
    if (renderer instanceof JComponent) {
      SwingUtilities.updateComponentTreeUI((JComponent)renderer);
    }
  }

  @Override
  public V getValue() throws Exception {
    if (myCbx.isEditable()) {
      final Component editorComponent = myCbx.getEditor().getEditorComponent();
      //noinspection unchecked
      return (V)((JTextField)editorComponent).getText();
    }
    else {
      //noinspection unchecked
      return (V)myCbx.getSelectedItem();
    }
  }

  private final class MyPopupMenuListener implements PopupMenuListener{
    private boolean myCancelled;

    @Override
    public void popupMenuWillBecomeVisible(final PopupMenuEvent e){
      myCancelled=false;
    }

    @Override
    public void popupMenuWillBecomeInvisible(final PopupMenuEvent e){
      if(!myCancelled){
        fireValueCommitted(true, true);
      }
    }

    @Override
    public void popupMenuCanceled(final PopupMenuEvent e){
      myCancelled=true;
    }
  }
}