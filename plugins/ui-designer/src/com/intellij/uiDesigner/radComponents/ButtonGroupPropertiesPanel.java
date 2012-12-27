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
      public void stateChanged(ChangeEvent e) {
        saveButtonGroupIsBound();
      }
    });
    myNameTextField.addFocusListener(new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        saveButtonGroupName();
      }
    });
    myNameTextField.addActionListener(new ActionListener() {
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
