package com.intellij.ui;

import javax.swing.*;

public class ListSpeedSearch extends SpeedSearchBase<JList> {
  public ListSpeedSearch(JList list) {
    super(list);
  }

  protected void selectElement(Object element, String selectedText) {
    ListScrollingUtil.selectItem(myComponent, element);
  }

  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  protected Object[] getAllElements() {
    ListModel model = myComponent.getModel();
    if (model instanceof DefaultListModel){ // optimization
      return ((DefaultListModel)model).toArray();
    }
    else{
      Object[] elements = new Object[model.getSize()];
      for(int i = 0; i < elements.length; i++){
        elements[i] = model.getElementAt(i);
      }
      return elements;
    }
  }

  protected String getElementText(Object element) {
    return element.toString();
  }
}