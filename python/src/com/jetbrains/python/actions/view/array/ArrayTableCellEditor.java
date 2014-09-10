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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * @author amarch
 */
class ArrayTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  Editor myEditor;
  Project myProject;
  Object lastValue;

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

    lastValue = value;

    myEditor =
      EditorFactory.getInstance().createEditor(new DocumentImpl(value.toString()), myProject, PythonFileType.INSTANCE, false);


    JComponent editorComponent = myEditor.getContentComponent();

    //todo: handle ENTER with action not listener
    editorComponent.addKeyListener(new KeyListener() {
      @Override
      public void keyTyped(KeyEvent e) {
      }

      @Override
      public void keyPressed(KeyEvent e) {
      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0) {
          doOKAction();
        }
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
  }

  public void cancelEditing() {
    new WriteCommandAction(null) {
      protected void run(@NotNull Result result) throws Throwable {
        myEditor.getDocument().setText(lastValue.toString());
      }
    }.execute();
    myEditor.getComponent().repaint();
    myEditor.getComponent().requestFocus();
  }
}
