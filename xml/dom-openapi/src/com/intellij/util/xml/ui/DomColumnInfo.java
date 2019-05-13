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
package com.intellij.util.xml.ui;

import com.intellij.util.ui.ColumnInfo;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * @author peter
 */
public abstract class DomColumnInfo<T, Aspect> extends ColumnInfo<T, Aspect> {
  private final TableCellRenderer myRenderer;

  public DomColumnInfo(String name) {
    this(name, new DefaultTableCellRenderer());
  }

  public DomColumnInfo(String name, final TableCellRenderer renderer) {
    super(name);
    myRenderer = renderer;
  }

  @Override
  public boolean isCellEditable(final T o) {
    return getEditor(o) != null;
  }

  @Override
  public TableCellRenderer getRenderer(T value) {
    return myRenderer;
  }

}
