// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BooleanEditor extends PropertyEditor<Boolean> {
  private final JCheckBox myCheckBox;
  private boolean myInsideChange;

  public BooleanEditor(){
    myCheckBox=new JCheckBox();
    myCheckBox.addActionListener(new MyActionListener());
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCheckBox);
  }

  @Override
  public Boolean getValue() throws Exception{
    return myCheckBox.isSelected();
  }

  @Override
  public JComponent getComponent(final RadComponent ignored, final Boolean value, final InplaceContext inplaceContext){
    myInsideChange=true;
    try{
      myCheckBox.setBackground(UIUtil.getTableBackground());
      myCheckBox.setSelected(value != null && value.booleanValue());
      return myCheckBox;
    }finally{
      myInsideChange=false;
    }
  }

  private final class MyActionListener implements ActionListener{
    @Override
    public void actionPerformed(final ActionEvent e){
      if(!myInsideChange){
        fireValueCommitted(true, false);
      }
    }
  }
}