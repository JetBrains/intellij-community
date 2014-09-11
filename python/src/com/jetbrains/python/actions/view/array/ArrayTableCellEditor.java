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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author amarch
 */
class ArrayTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  MyTableEditor myEditor;
  Project myProject;
  Object lastValue;

  public ArrayTableCellEditor(Project project) {
    super();
    myProject = project;
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                               int rowIndex, int vColIndex) {


    myEditor = new MyTableEditor(myProject, new PyDebuggerEditorsProvider(), "arrayTableView", null,
                                 new XExpressionImpl(value.toString(), PythonLanguage.getInstance(), "", EvaluationMode.CODE_FRAGMENT));

    lastValue = value;
    JComponent editorComponent = myEditor.getComponent();

    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "strokeEnter");
    editorComponent.getActionMap().put("strokeEnter", new AbstractAction() {
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
    if (myEditor.getEditor() != null) {
      return myEditor.getEditor().getDocument().getText();
    }
    else {
      return null;
    }
  }

  public void doOKAction() {
  }

  public void cancelEditing() {
    new WriteCommandAction(null) {
      protected void run(@NotNull Result result) throws Throwable {
        if (myEditor.getEditor() != null) {
          myEditor.getEditor().getDocument().setText(lastValue.toString());
        }
      }
    }.execute();
    myEditor.getComponent().repaint();
    myEditor.getComponent().requestFocus();
  }

  public class MyTableEditor extends XDebuggerEditorBase {
    private final EditorTextField myEditorTextField;
    private XExpression myExpression;

    public MyTableEditor(Project project,
                         XDebuggerEditorsProvider debuggerEditorsProvider,
                         @Nullable @NonNls String historyId,
                         @Nullable XSourcePosition sourcePosition, @NotNull XExpression text) {
      super(project, debuggerEditorsProvider, EvaluationMode.CODE_FRAGMENT, historyId, sourcePosition);
      myExpression = XExpressionImpl.changeMode(text, getMode());
      myEditorTextField = new EditorTextField(createDocument(myExpression), project, debuggerEditorsProvider.getFileType()) {
        @Override
        protected EditorEx createEditor() {
          final EditorEx editor = super.createEditor();
          editor.setVerticalScrollbarVisible(false);
          editor.setOneLineMode(true);
          return editor;
        }

        @Override
        protected boolean isOneLineMode() {
          return false;
        }
      };
      myEditorTextField.setFontInheritedFromLAF(false);
    }

    @Override
    public JComponent getComponent() {
      return myEditorTextField;
    }

    @Override
    protected void doSetText(XExpression text) {
      myEditorTextField.setText(text.getExpression());
    }

    @Override
    public XExpression getExpression() {
      return getEditorsProvider()
        .createExpression(getProject(), myEditorTextField.getDocument(), myExpression.getLanguage(), EvaluationMode.CODE_FRAGMENT);
    }

    @Override
    @Nullable
    public JComponent getPreferredFocusedComponent() {
      final Editor editor = myEditorTextField.getEditor();
      return editor != null ? editor.getContentComponent() : null;
    }

    @Nullable
    @Override
    public Editor getEditor() {
      return myEditorTextField.getEditor();
    }

    @Override
    public void selectAll() {
      myEditorTextField.selectAll();
    }
  }
}
