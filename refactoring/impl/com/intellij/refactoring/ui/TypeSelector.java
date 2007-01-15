package com.intellij.refactoring.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemListener;

/**
 * @author dsl
 */
public class TypeSelector {
  private final PsiType myType;
  private final JComponent myComponent;
  private final MyComboBoxModel myComboBoxModel;

  public TypeSelector(PsiType type) {
    myType = type;
    myComponent = new JLabel(myType.getCanonicalText());
    myComboBoxModel = null;
  }

  public TypeSelector() {
    myComboBoxModel = new MyComboBoxModel();
    myComponent = new ComboBox();
    ((ComboBox) myComponent).setModel(myComboBoxModel);
    ((ComboBox) myComponent).setRenderer(new MyListCellRenderer());
    myType = null;
  }

  public void setTypes(PsiType[] types) {
    if(myComboBoxModel == null) return;
    PsiType oldType;
    if (myComboBoxModel.getSize() > 0) {
      oldType = (PsiType) myComboBoxModel.getSelectedItem();
    } else {
      oldType = null;
    }
    myComboBoxModel.setSuggestions(types);
    if(oldType != null) {
      for (int i = 0; i < types.length; i++) {
        PsiType type = types[i];
        if(type.equals(oldType)) {
          ((JComboBox) myComponent).setSelectedIndex(i);
          return;
        }
      }
    }
    ((JComboBox) myComponent).setSelectedIndex(0);
  }


  public void addItemListener(ItemListener aListener) {
    if(myComponent instanceof JComboBox) {
      ((JComboBox) myComponent).addItemListener(aListener);
    }
  }

  public void removeItemListener(ItemListener aListener) {
    if (myComponent instanceof JComboBox) {
      ((JComboBox) myComponent).removeItemListener(aListener);
    }
  }

  public ItemListener[] getItemListeners() {
    if (myComponent instanceof JComboBox) {
      return ((JComboBox) myComponent).getItemListeners();
    } else {
      return new ItemListener[0];
    }
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public JComponent getFocusableComponent() {
    if (myComponent instanceof JComboBox) {
      return myComponent;
    } else {
      return null;
    }
  }

  public PsiType getSelectedType() {
    if (myComponent instanceof JLabel) {
      return myType;
    } else {
      return (PsiType) ((JComboBox) myComponent).getSelectedItem();
    }
  }

  private static class MyComboBoxModel extends DefaultComboBoxModel {
    private PsiType[] mySuggestions;

    MyComboBoxModel() {
      mySuggestions = new PsiType[0];
    }

    // implements javax.swing.ListModel
    public int getSize() {
      return mySuggestions.length;
    }

    // implements javax.swing.ListModel
    public Object getElementAt(int index) {
      return mySuggestions[index];
    }

    public void setSuggestions(PsiType[] suggestions) {
      fireIntervalRemoved(this, 0, mySuggestions.length);
      mySuggestions = suggestions;
      fireIntervalAdded(this, 0, mySuggestions.length);
    }
  }



  private class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(
            JList list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (value != null) {
        setText(((PsiType) value).getPresentableText());
      }

      return this;
    }
  }

}
