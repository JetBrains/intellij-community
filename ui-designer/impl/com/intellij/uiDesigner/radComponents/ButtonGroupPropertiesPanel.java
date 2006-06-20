/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yole
 */
public class ButtonGroupPropertiesPanel implements CustomPropertiesPanel {
  private JTextField myNameTextField;
  private JCheckBox myBindToFieldCheckBox;
  private JPanel myPanel;
  private final RadButtonGroup myGroup;
  private CopyOnWriteArrayList<ChangeListener> myListeners = new CopyOnWriteArrayList<ChangeListener>();

  public ButtonGroupPropertiesPanel(final RadRootContainer rootContainer, final RadButtonGroup group) {
    myGroup = group;
    myNameTextField.setText(group.getName());
    myBindToFieldCheckBox.setSelected(group.isBound());
    myBindToFieldCheckBox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myGroup.setBound(myBindToFieldCheckBox.isSelected());
        notifyListeners(new ChangeEvent(myGroup));
      }
    });
    myNameTextField.addFocusListener(new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        String oldName = myGroup.getName();
        String newName = myNameTextField.getText();
        if (!oldName.equals(newName)) {
          myGroup.setName(newName);
          notifyListeners(new ChangeEvent(myGroup));
          if (myGroup.isBound()) {
            BindingProperty.updateBoundFieldName(rootContainer, oldName, newName, ButtonGroup.class.getName());
          }
        }
      }
    });
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void addChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  private void notifyListeners(final ChangeEvent event) {
    for (ChangeListener changeListener : myListeners) {
      changeListener.stateChanged(event);
    }
  }
}
