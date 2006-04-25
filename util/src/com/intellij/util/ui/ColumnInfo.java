/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class ColumnInfo <Item, Aspect> {

  public final static class StringColumn extends ColumnInfo<String, String> {
      public StringColumn(final String name) {
          super(name);
      }

      public String valueOf(final String item) {
          return item;
      }
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.ColumnInfo");
  private String myName;
  public static ColumnInfo[] EMPTY_ARRAY = new ColumnInfo[0];

  public ColumnInfo(String name) {
    myName = name;
  }

  public String toString() {
    return getName();
  }

  public abstract Aspect valueOf(Item item);

  public final boolean isSortable() {
    return getComparator() != null;
  }

  public Comparator<Item> getComparator(){
    return null;
  }

  public String getName() {
    return myName;
  }

  public void sort(List<Item> list) {
    LOG.assertTrue(list != null);
    Comparator<Item> comparator = getComparator();
    if (comparator != null) {
      Collections.sort(list, comparator);
    }
  }

  public Class getColumnClass() {
    return String.class;
  }

  public boolean isCellEditable(Item item) {
    return false;
  }

  public void setValue(Item item, Aspect value) {

  }

  public TableCellRenderer getRenderer(Item item) {
    return null;
  }

  public TableCellRenderer getCustomizedRenderer(final Item o, TableCellRenderer renderer) {
    return renderer;
  }

  public TableCellEditor getEditor(Item o) {
    return null;
  }

  public String getMaxStringValue() {
    return null;
  }

  public int getAdditionalWidth() {
    return 0;
  }

  public int getWidth(JTable table) {
    return -1;
  }

  public void setName(String s) {
    myName = s;
  }

  @Nullable
  public String getTooltipText() {
    return null;
  }

}
