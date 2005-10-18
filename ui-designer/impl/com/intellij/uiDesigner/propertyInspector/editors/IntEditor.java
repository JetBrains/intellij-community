package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntEditor extends PropertyEditor{
  private final int myLowBoundary;
  private final JTextField myTf;

  /**
   * @param lowBondary minimal integer value that editor accepts.
   */
  public IntEditor(final int lowBondary){
    myLowBoundary = lowBondary;
    myTf = new JTextField();
    myTf.addActionListener(new MyActionListener());
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTf);
  }

  public JComponent getComponent(final RadComponent ignored, final Object value, final boolean inplace){
    final Integer integer = (Integer)value;
    myTf.setText(integer.toString());

    if(inplace){
      //noinspection HardCodedStringLiteral
      myTf.setBorder(UIUtil.getTextFieldBorder());
    }
    else{
      myTf.setBorder(null);
    }

    return myTf;
  }

  public Object getValue() throws Exception{
    try {
      final Integer value = Integer.valueOf(myTf.getText());
      if(value.intValue() < myLowBoundary){
        throw new RuntimeException(UIDesignerBundle.message("error.value.should.not.be.less", myLowBoundary));
      }
      return value;
    }
    catch (final NumberFormatException exc) {
      throw new RuntimeException(UIDesignerBundle.message("error.not.an.integer"));
    }
  }

  private final class MyActionListener implements ActionListener{
    public void actionPerformed(final ActionEvent e){
      fireValueCommited();
    }
  }
}
