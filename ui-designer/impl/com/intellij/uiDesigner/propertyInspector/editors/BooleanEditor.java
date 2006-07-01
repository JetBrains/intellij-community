package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class BooleanEditor extends PropertyEditor<Boolean> {
  private final JCheckBox myCheckBox;
  private boolean myInsideChange;

  public BooleanEditor(){
    myCheckBox=new JCheckBox();
    myCheckBox.addActionListener(new MyActionListener());
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCheckBox);
  }

  public Boolean getValue() throws Exception{
    return myCheckBox.isSelected();
  }

  public JComponent getComponent(final RadComponent ignored, final Boolean value, final boolean inplace){
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
    public void actionPerformed(final ActionEvent e){
      if(!myInsideChange){
        fireValueCommitted(true);
      }
    }
  }
}