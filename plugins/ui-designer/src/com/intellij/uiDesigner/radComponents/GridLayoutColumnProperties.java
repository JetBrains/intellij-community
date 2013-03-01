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

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.properties.HSizePolicyProperty;
import com.intellij.uiDesigner.propertyInspector.properties.VSizePolicyProperty;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author yole
 */
public class GridLayoutColumnProperties implements CustomPropertiesPanel {
  private JPanel myRootPanel;
  private JCheckBox myWantGrowCheckBox;
  private JLabel myTitleLabel;
  private RadContainer myContainer;
  private boolean myRow;
  private int mySelectedIndex;
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public GridLayoutColumnProperties() {
    myWantGrowCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        for(RadComponent c: myContainer.getComponents()) {
          if (c.getConstraints().getCell(myRow) == mySelectedIndex) {
            Property<RadComponent, Integer> property = myRow
                                                       ? VSizePolicyProperty.getInstance(c.getProject())
                                                       : HSizePolicyProperty.getInstance(c.getProject());
            if (myWantGrowCheckBox.isSelected()) {
              property.setValueEx(c, property.getValue(c).intValue() | GridConstraints.SIZEPOLICY_WANT_GROW);
              break;
            }
            else {
              if ((property.getValue(c).intValue() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
                property.setValueEx(c, property.getValue(c).intValue() & ~GridConstraints.SIZEPOLICY_WANT_GROW);
                break;
              }
            }
          }
        }
        for(ChangeListener listener: myListeners) {
          listener.stateChanged(new ChangeEvent(this));
        }
      }
    });
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  public void showProperties(RadContainer container, boolean isRow, int[] selectedIndices) {
    myContainer = container;
    myRow = isRow;
    if (selectedIndices.length != 1) {
      myTitleLabel.setText(selectedIndices.length + (isRow ? " rows selected" : " columns selected"));
      myWantGrowCheckBox.setEnabled(false);
    }
    else {
      mySelectedIndex = selectedIndices [0];
      myTitleLabel.setText((isRow ? "Row " : "Column ") + selectedIndices [0]);
      myWantGrowCheckBox.setEnabled(true);

      GridLayoutManager layout = (GridLayoutManager) container.getLayout();
      int sizePolicy = layout.getCellSizePolicy(isRow, selectedIndices [0]);
      myWantGrowCheckBox.setSelected((sizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0);
    }
  }

  public void addChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }
}
