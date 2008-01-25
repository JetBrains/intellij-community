package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;

import javax.swing.*;

/**
 * @author nik
 */
public class XDebuggerExpressionComboBox extends XDebuggerEditorBase {
  private final ComboBox myComboBox;
  private EditorComboBoxEditor myEditor;
  private final Project myProject;
  private final XDebuggerEditorsProvider myDebuggerEditorsProvider;
  private String myExpression;

  public XDebuggerExpressionComboBox(final Project project, final XDebuggerEditorsProvider debuggerEditorsProvider) {
    super(project, debuggerEditorsProvider);
    myProject = project;
    myDebuggerEditorsProvider = debuggerEditorsProvider;
    myComboBox = new ComboBox();
    myComboBox.setEditable(true);
    myExpression = "";
    initEditor();
  }

  public JComponent getComponent() {
    return myComboBox;
  }

  public void setEnabled(boolean enable) {
    if (enable == myComboBox.isEnabled()) return;

    myComboBox.setEnabled(enable);
    myComboBox.setEditable(enable);

    if (enable) {
      initEditor();
    }
    else {
      myExpression = getText();
    }
  }

  private void initEditor() {
    myEditor = new EditorComboBoxEditor(myProject, myDebuggerEditorsProvider.getFileType()) {
      public void setItem(Object anObject) {
        if (anObject == null) {
          anObject = createDocument("");
        }
        super.setItem(anObject);
      }
    };
    myComboBox.setEditor(myEditor);
    myEditor.setItem(createDocument(myExpression));
    myComboBox.setRenderer(new EditorComboBoxRenderer(myEditor));
    myComboBox.setMaximumRowCount(20);
  }

  public void setText(final String text) {
    if (myComboBox.isEditable()) {
      myEditor.setItem(createDocument(text));
    }
    else {
      myExpression = text;
    }
  }

  public String getText() {
    return ((Document)myEditor.getItem()).getText();
  }
}
