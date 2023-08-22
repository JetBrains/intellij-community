// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.array;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author amarch
 */
public class ArrayTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private MyTableEditor myEditor;
  private final Project myProject;
  private Object myLastValue;

  private static final Logger LOG = Logger.getInstance(ArrayTableCellEditor.class);

  public ArrayTableCellEditor(Project project) {
    myProject = project;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                               final int rowIndex, final int vColIndex) {
    myEditor = new MyTableEditor(myProject, new PyDebuggerEditorsProvider(), "numpy.array.table.view", null,
                                 new XExpressionImpl(value.toString(), PythonLanguage.getInstance(), "", EvaluationMode.CODE_FRAGMENT),
                                 getActionsAdapter(rowIndex, vColIndex));
    myLastValue = value;
    return myEditor.getComponent();
  }

  @Override
  @Nullable
  @NlsSafe
  public Object getCellEditorValue() {
    if (myEditor.getEditor() != null) {
      return myEditor.getEditor().getDocument().getText();
    }
    return null;
  }

  @NotNull
  private KeyAdapter getActionsAdapter(final int rowIndex, final int vColIndex) {
    return new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          doOKAction(rowIndex, vColIndex);
        }
        else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          cancelEditing();
        }
      }
    };
  }

  public void doOKAction(int rowIndex, int vColIndex) {
  }

  public void cancelEditing() {
    WriteCommandAction.writeCommandAction(null).run(() -> {
      if (myEditor.getEditor() != null) {
        myEditor.getEditor().getDocument().setText(myLastValue.toString());
      }
    });
    myEditor.getComponent().repaint();
    stopCellEditing();
  }

  public MyTableEditor getEditor() {
    return myEditor;
  }

  public void setLastValue(Object lastValue) {
    myLastValue = lastValue;
  }

  public static class MyTableEditor extends XDebuggerEditorBase {
    private final EditorTextField myEditorTextField;
    private final XExpression myExpression;

    public MyTableEditor(Project project,
                         XDebuggerEditorsProvider debuggerEditorsProvider,
                         @Nullable @NonNls String historyId,
                         @Nullable XSourcePosition sourcePosition, @NotNull XExpression text, @NotNull final KeyAdapter actionAdapter) {
      super(project, debuggerEditorsProvider, EvaluationMode.CODE_FRAGMENT, historyId, sourcePosition);
      myExpression = XExpressionImpl.changeMode(text, getMode());
      myEditorTextField = new EditorTextField(createDocument(myExpression), project, debuggerEditorsProvider.getFileType()) {
        @Override
        protected @NotNull EditorEx createEditor() {
          final EditorEx editor = super.createEditor();
          editor.setVerticalScrollbarVisible(false);
          editor.setOneLineMode(true);
          editor.getContentComponent().addKeyListener(actionAdapter);
          return editor;
        }

        @Override
        protected boolean isOneLineMode() {
          return true;
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
