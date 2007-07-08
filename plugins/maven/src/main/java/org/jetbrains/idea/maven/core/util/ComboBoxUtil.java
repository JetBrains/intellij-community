package org.jetbrains.idea.maven.core.util;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ComboBoxUtil {

  private static class Item {
    private final String value;
    private final String label;

    private Item(String value, String label) {
      this.value = value;
      this.label = label;
    }

    public String getValue() {
      return value;
    }

    public String toString() {
      return label;
    }
  }

  public static void addToModel(DefaultComboBoxModel model, Object value, String label) {
    model.addElement(new Item(String.valueOf(value), label));
  }

  public static void addToModel(DefaultComboBoxModel model, Object[][] array) {
    for (Object[] objects : array) {
      addToModel(model, objects[0], String.valueOf(objects[1]));
    }
  }

  public static void initModel(DefaultComboBoxModel model, Object[][] array) {
    model.removeAllElements();
    addToModel(model, array);
  }

  public static void setModel(JComboBox comboBox, DefaultComboBoxModel model, Object[][] array) {
    initModel(model, array);
    comboBox.setModel(model);
  }

  public static void select(DefaultComboBoxModel model, String value) {
    for (int i = 0; i < model.getSize(); i++) {
      Item comboBoxUtil = (Item)model.getElementAt(i);
      if (comboBoxUtil.getValue().equals(value)) {
        model.setSelectedItem(comboBoxUtil);
        return;
      }
    }
    if (model.getSize() != 0) {
      model.setSelectedItem(model.getElementAt(0));
    }
  }

  @Nullable
  public static String getSelectedString(DefaultComboBoxModel model) {
    final Object item = model.getSelectedItem();
    return item != null ? ((Item)item).getValue() : null;
  }
}
