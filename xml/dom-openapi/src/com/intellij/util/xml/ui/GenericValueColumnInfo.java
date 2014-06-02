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

import com.intellij.util.xml.GenericDomValue;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * @author peter
 */
public class GenericValueColumnInfo<T> extends DomColumnInfo<GenericDomValue<T>, String> {
  private final Class<T> myColumnClass;
  private final TableCellEditor myEditor;

  public GenericValueColumnInfo(final String name, final Class<T> columnClass, final TableCellRenderer renderer, final TableCellEditor editor) {
    super(name, renderer);
    myColumnClass = columnClass;
    myEditor = editor;
  }

  public GenericValueColumnInfo(final String name, final Class<T> columnClass, final TableCellEditor editor) {
    this(name, columnClass, new DefaultTableCellRenderer(), editor);
  }

  @Override
  public final TableCellEditor getEditor(GenericDomValue<T> value) {
    return myEditor;
  }

  @Override
  public final Class<T> getColumnClass() {
    return myColumnClass;
  }

  @Override
  public final void setValue(final GenericDomValue<T> o, final String aValue) {
    o.setStringValue(aValue);
  }

  @Override
  public final String valueOf(GenericDomValue<T> object) {
    return object.getStringValue();
  }
}
