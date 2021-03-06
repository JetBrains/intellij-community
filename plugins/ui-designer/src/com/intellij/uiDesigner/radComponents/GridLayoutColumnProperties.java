// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.UIDesignerBundle;
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
      @Override
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

  @Override
  public JComponent getComponent() {
    return myRootPanel;
  }

  public void showProperties(RadContainer container, boolean isRow, int[] selectedIndices) {
    myContainer = container;
    myRow = isRow;
    if (selectedIndices.length != 1) {
      myTitleLabel.setText(UIDesignerBundle.message("grid.layout.column.selected.property", selectedIndices.length, isRow ? 0 : 1));
      myWantGrowCheckBox.setEnabled(false);
    }
    else {
      mySelectedIndex = selectedIndices [0];
      myTitleLabel.setText(UIDesignerBundle.message("grid.layout.column.property", isRow ? 0 : 1, selectedIndices [0]));
      myWantGrowCheckBox.setEnabled(true);

      GridLayoutManager layout = (GridLayoutManager) container.getLayout();
      int sizePolicy = layout.getCellSizePolicy(isRow, selectedIndices [0]);
      myWantGrowCheckBox.setSelected((sizePolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0);
    }
  }

  @Override
  public void addChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }
}
