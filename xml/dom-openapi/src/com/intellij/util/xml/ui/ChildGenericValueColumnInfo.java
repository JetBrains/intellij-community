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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * @author peter
 */
public class ChildGenericValueColumnInfo<T extends DomElement> extends DomColumnInfo<T, String> {
  private final TableCellEditor myEditor;
  private final DomFixedChildDescription myChildDescription;

  public ChildGenericValueColumnInfo(final String name,
                                     @NotNull final DomFixedChildDescription description,
                                     final TableCellRenderer renderer,
                                     final TableCellEditor editor) {
    super(name, renderer);
    myEditor = editor;
    myChildDescription = description;
  }

  public ChildGenericValueColumnInfo(final String name, final DomFixedChildDescription description, final TableCellEditor editor) {
    this(name, description, new DefaultTableCellRenderer(), editor);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final ChildGenericValueColumnInfo that = (ChildGenericValueColumnInfo)o;

    if (!myChildDescription.equals(that.myChildDescription)) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myChildDescription.hashCode();
    return result;
  }

  @Override
  public final TableCellEditor getEditor(T value) {
    return myEditor;
  }

  @Override
  public final Class<T> getColumnClass() {
    return (Class<T>)ReflectionUtil.getRawType(myChildDescription.getType());
  }

  @Override
  public TableCellRenderer getCustomizedRenderer(final T domElement, final TableCellRenderer renderer) {
    assert domElement.isValid();
    return getErrorableCellRenderer(renderer, domElement);
  }

  public DefaultTableCellRenderer getErrorableCellRenderer(final TableCellRenderer renderer, final T domElement) {
    return new ErrorableTableCellRenderer<>(getGenericValue(domElement), renderer, domElement);
  }

  @Override
  public void setValue(final T o, final String aValue) {
    getGenericValue(o).setStringValue(aValue);
  }

  protected final GenericDomValue getGenericValue(final T o) {
    return (GenericDomValue)myChildDescription.getValues(o).get(0);
  }

  @Override
  public final String valueOf(T object) {
    if (!object.isValid()) return null;
    final String stringValue = getGenericValue(object).getStringValue();
    return StringUtil.isEmpty(stringValue) ? getEmptyValuePresentation(object) : stringValue;
  }

  protected final DomFixedChildDescription getChildDescription() {
    return myChildDescription;
  }

  protected String getEmptyValuePresentation(T object) {
    return "";
  }

}
