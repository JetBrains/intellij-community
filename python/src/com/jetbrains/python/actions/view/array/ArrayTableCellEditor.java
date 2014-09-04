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
package com.jetbrains.python.actions.view.array;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PythonFileType;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
* @author amarch
*/
class ArrayTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  Editor myEditor;
  Project myProject;

  public ArrayTableCellEditor(Project project) {
    super();
    myProject = project;
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                               int rowIndex, int vColIndex) {


    //PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(myProject, "array_view.py", value.toString(), true);
    //
    //myEditor = EditorFactoryImpl.getInstance().
    //  createEditor(PsiDocumentManager.getInstance(myProject).getDocument(fragment), myProject);


    myEditor =
      EditorFactoryImpl.getInstance().createEditor(new DocumentImpl(value.toString()), myProject, PythonFileType.INSTANCE, false);


    JComponent editorComponent = myEditor.getContentComponent();

    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enterStroke");
    editorComponent.getActionMap().put("enterStroke", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doOKAction();
      }
    });
    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeStroke");
    editorComponent.getActionMap().put("escapeStroke", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cancelEditing();
      }
    });

    return editorComponent;
  }

  public Object getCellEditorValue() {
    return myEditor.getDocument().getText();
  }

  public void doOKAction() {
    //todo: not performed
    System.out.println("ok");
  }

  public void cancelEditing() {
    System.out.println("esc");
  }
}
