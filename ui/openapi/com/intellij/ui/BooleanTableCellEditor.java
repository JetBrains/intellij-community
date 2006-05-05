/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
package com.intellij.ui;

import javax.swing.*;

/**
 * @author peter
 */
public class BooleanTableCellEditor extends DefaultCellEditor {

  private final boolean myStringEditor;

  public BooleanTableCellEditor(boolean isStringEditor) {
    super(new JCheckBox());
    myStringEditor = isStringEditor;
    ((JCheckBox) editorComponent).setHorizontalAlignment(SwingConstants.CENTER);
  }

  public BooleanTableCellEditor() {
    this(false);
  }

  public Object getCellEditorValue() {
    Object value = super.getCellEditorValue();
    if (myStringEditor && value instanceof Boolean) {
      return value.toString();
    } else {
      return value;
    }
  }
}
