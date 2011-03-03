/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class ComboBoxUtil {

  private static class Item {
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

  public static <T> void setModel(JComboBox comboBox, DefaultComboBoxModel model, Collection<T> values, Function<T, Pair<String, ?>> func) {
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
