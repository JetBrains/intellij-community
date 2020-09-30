// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class ComboBoxUtil {

  private static final class Item {
    private final Object value;
    private final String label;

    private Item(Object value, String label) {
      this.value = value;
      this.label = label;
    }

    public Object getValue() {
      return value;
    }

    public String toString() {
      return label;
    }
  }

  public static void addToModel(DefaultComboBoxModel model, Object value, String label) {
    model.addElement(new Item(value, label));
  }

  public static <T> void setModel(JComboBox comboBox, DefaultComboBoxModel model, Collection<? extends T> values, Function<? super T, ? extends Pair<String, ?>> func) {
    model.removeAllElements();
    for (T each : values) {
      Pair<String, ?> pair = func.fun(each);
      addToModel(model, pair.second, pair.first);
    }
    comboBox.setModel(model);
  }

  public static void select(DefaultComboBoxModel model, Object value) {
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
    return String.valueOf(getSelectedValue(model));
  }

  @Nullable
  public static Object getSelectedValue(DefaultComboBoxModel model) {
    final Object item = model.getSelectedItem();
    return item != null ? ((Item)item).getValue() : null;
  }
}
