package com.intellij.refactoring.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
    myComponent = new JLabel(myType.getPresentableText());
    myComboBoxModel = null;
  }

  public TypeSelector() {
    myComboBoxModel = new MyComboBoxModel();
    myComponent = new ComboBox();
    ((ComboBox) myComponent).setModel(myComboBoxModel);
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
    myComboBoxModel.setSuggestions(wrapToItems(types));
    if(oldType != null) {
      for (int i = 0; i < types.length; i++) {
        PsiType type = types[i];
        if(type.equals(oldType)) {
          ((JComboBox) myComponent).setSelectedIndex(i);
          return;
        }
      }
    }
    if (types.length > 0) {
      ((JComboBox) myComponent).setSelectedIndex(0);
    }
  }

  private static PsiTypeItem[] wrapToItems(final PsiType[] types) {
    PsiTypeItem[] result = new PsiTypeItem[types.length];
    for (int i = 0; i < result.length; i++) {
      result [i] = new PsiTypeItem(types [i]);
    }
    return result;
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

  @Nullable
  public PsiType getSelectedType() {
    if (myComponent instanceof JLabel) {
      return myType;
    } else {
      final PsiTypeItem selItem = (PsiTypeItem)((JComboBox)myComponent).getSelectedItem();
      return selItem == null ? null : selItem.getType();
    }
  }

  private static class MyComboBoxModel extends DefaultComboBoxModel {
    private PsiTypeItem[] mySuggestions;

    MyComboBoxModel() {
      mySuggestions = new PsiTypeItem[0];
    }

    // implements javax.swing.ListModel
    public int getSize() {
      return mySuggestions.length;
    }

    // implements javax.swing.ListModel
    public Object getElementAt(int index) {
      return mySuggestions[index];
    }

    public void setSuggestions(PsiTypeItem[] suggestions) {
      fireIntervalRemoved(this, 0, mySuggestions.length);
      mySuggestions = suggestions;
      fireIntervalAdded(this, 0, mySuggestions.length);
    }
  }

  private static class PsiTypeItem {
    private final PsiType myType;

    private PsiTypeItem(final PsiType type) {
      myType = type;
    }

    public PsiType getType() {
      return myType;
    }

    @Override
    public String toString() {
      return myType.getPresentableText();
    }
  }

}
