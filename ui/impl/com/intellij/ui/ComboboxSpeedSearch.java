/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 11-Jul-2006
 * Time: 13:56:13
 */
package com.intellij.ui;

import javax.swing.*;

public class ComboboxSpeedSearch extends SpeedSearchBase<JComboBox> {
  public ComboboxSpeedSearch(JComboBox comboBox) {
    super(comboBox);
  }

  protected void selectElement(Object element, String selectedText) {
    myComponent.setSelectedItem(element);
  }

  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  protected Object[] getAllElements() {
    ListModel model = myComponent.getModel();
    Object[] elements = new Object[model.getSize()];
    for(int i = 0; i < elements.length; i++){
      elements[i] = model.getElementAt(i);
    }
    return elements;
  }

  protected String getElementText(Object element) {
    return element.toString();
  }
}