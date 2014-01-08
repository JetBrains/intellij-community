/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.browsers;

import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.ListTableModel;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TableModelEditor<T> implements ElementProducer<T> {
  private final List<T> items;
  private final TableView<T> table;

  private final THashMap<T, T> modifiedItems = new THashMap<T, T>();
  private final Function<T, T> mutableFactory;
  private final Class<T> itemClass;

  private boolean isApplying;

  /**
   * source will be copied, passed list will not be used directly
   * itemClass must has empty constructor
   */
  public TableModelEditor(@NotNull List<T> source, @NotNull ColumnInfo[] columns, @NotNull Function<T, T> mutableFactory, Class<T> itemClass) {
    this.itemClass = itemClass;
    items = new ArrayList<T>(source);
    this.mutableFactory = mutableFactory;

    table = new TableView<T>(new ListTableModel<T>(columns, items));
    table.setStriped(true);
    new TableSpeedSearch(table);
    if (columns[0].getColumnClass() == Boolean.class && columns[0].getName().isEmpty()) {
      TableUtil.setupCheckboxColumn(table.getColumnModel().getColumn(0));
    }
  }

  public abstract static class EditableColumnInfo<Item, Aspect> extends ColumnInfo<Item, Aspect> {
    public EditableColumnInfo(@NotNull String name) {
      super(name);
    }

    public EditableColumnInfo() {
      super("");
    }

    @Override
    public boolean isCellEditable(Item item) {
      return true;
    }
  }

  @NotNull
  public JComponent createComponent() {
    return ToolbarDecorator.createDecorator(table, this).createPanel();
  }

  @Override
  public T createElement() {
    try {
      return itemClass.newInstance();
    }
    catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean canCreateElement() {
    return true;
  }

  @NotNull
  public T getEffective(@NotNull T item) {
    T mutable = isApplying || modifiedItems.isEmpty() ? null : modifiedItems.get(item);
    return mutable == null ? item : mutable;
  }

  @NotNull
  public T getMutable(@NotNull T item) {
    if (isApplying) {
      return item;
    }

    T mutable = modifiedItems.get(item);
    if (mutable == null) {
      mutable = mutableFactory.fun(item);
      modifiedItems.put(item, mutable);
    }
    return mutable;
  }

  public boolean isModified(@NotNull List<T> oldItems) {
    if (!modifiedItems.isEmpty()) {
      for (Map.Entry<T, T> entry : modifiedItems.entrySet()) {
        if (entry.getValue().equals(entry.getKey())) {
          return true;
        }
      }
    }

    // is order changed or new items added?
    if (items.size() == oldItems.size()) {
      for (int i = 0, size = items.size(); i < size; i++) {
        if (items.get(i) != oldItems.get(i)) {
          return true;
        }
      }
    }
    else {
      return true;
    }

    return false;
  }

  @NotNull
  public List<T> apply() {
    if (!modifiedItems.isEmpty()) {
      isApplying = true;

      @SuppressWarnings("unchecked")
      final ColumnInfo<T, Object>[] columns = ((ListTableModel)table.getModel()).getColumnInfos();
      modifiedItems.forEachEntry(new TObjectObjectProcedure<T, T>() {
        @Override
        public boolean execute(T item, T newItem) {
          for (ColumnInfo<T, Object> column : columns) {
            if (column.isCellEditable(item)) {
              column.setValue(item, column.valueOf(newItem));
            }
          }
          return true;
        }
      });

      isApplying = false;
      modifiedItems.clear();
    }
    return items;
  }

  public void clear() {
    modifiedItems.clear();
  }
}