package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BooleanEditor extends PropertyEditor{
  private final JCheckBox myCheckBox;
  private boolean myInsideChange;

  public BooleanEditor(){
    myCheckBox=new JCheckBox();
    myCheckBox.addActionListener(new MyActionListener());
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCheckBox);
  }

  public Object getValue() throws Exception{
    return Boolean.valueOf(myCheckBox.isSelected());
  }

  public JComponent getComponent(final RadComponent ignored, final Object value, final boolean inplace){
    myInsideChange=true;
    try{
      myCheckBox.setBackground(UIManager.getColor("Table.background"));
      myCheckBox.setSelected(((Boolean)value).booleanValue());
      return myCheckBox;
    }finally{
      myInsideChange=false;
    }
  }

  private final class MyActionListener implements ActionListener{
    public void actionPerformed(final ActionEvent e){
      if(!myInsideChange){
        fireValueCommited();
      }
    }
  }
}