// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

/**
 * @author yole
 */
public class ButtonGroupPropertiesPanel implements CustomPropertiesPanel {
  private JTextField myNameTextField;
  private JCheckBox myBindToFieldCheckBox;
  private JPanel myPanel;
  private final RadRootContainer myRootContainer;
  private final RadButtonGroup myGroup;
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public ButtonGroupPropertiesPanel(final RadRootContainer rootContainer, final RadButtonGroup group) {
    myRootContainer = rootContainer;
    myGroup = group;
    myNameTextField.setText(group.getName());
    myBindToFieldCheckBox.setSelected(group.isBound());
    myBindToFieldCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        saveButtonGroupIsBound();
      }
    });
    myNameTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        saveButtonGroupName();
      }
    });
    myNameTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        saveButtonGroupName();
      }
    });
  }

  private void saveButtonGroupIsBound() {
    if (myGroup.isBound() != myBindToFieldCheckBox.isSelected()) {
      myGroup.setBound(myBindToFieldCheckBox.isSelected());
      notifyListeners(new ChangeEvent(myGroup));
      if (myGroup.isBound()) {
        BindingProperty.updateBoundFieldName(myRootContainer, null, myGroup.getName(), ButtonGroup.class.getName());
      }
      else {
        BindingProperty.checkRemoveUnusedField(myRootContainer, myGroup.getName(),
                                               FormEditingUtil.getNextSaveUndoGroupId(myRootContainer.getProject()));
      }
    }
  }

  private void saveButtonGroupName() {
    String oldName = myGroup.getName();
    String newName = myNameTextField.getText();
    if (!oldName.equals(newName)) {
      myGroup.setName(newName);
      notifyListeners(new ChangeEvent(myGroup));
      if (myGroup.isBound()) {
        BindingProperty.updateBoundFieldName(myRootContainer, oldName, newName, ButtonGroup.class.getName());
      }
    }
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void addChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  private void notifyListeners(final ChangeEvent event) {
    for (ChangeListener changeListener : myListeners) {
      changeListener.stateChanged(event);
    }
  }
}
