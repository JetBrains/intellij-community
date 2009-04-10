package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.EditorTextField;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class XDebuggerMultilineEditor extends XDebuggerEditorBase {
  private final EditorTextField myEditorTextField;

  public XDebuggerMultilineEditor(Project project,
                                   XDebuggerEditorsProvider debuggerEditorsProvider,
                                   @Nullable @NonNls String historyId,
                                   @Nullable XSourcePosition sourcePosition, @NotNull String text) {
    super(project, debuggerEditorsProvider, historyId, sourcePosition);
    myEditorTextField = new EditorTextField(createDocument(text), project, debuggerEditorsProvider.getFileType()) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.setOneLineMode(false);
        editor.setVerticalScrollbarVisible(true);
        return editor;
      }
    };
  }

  public JComponent getComponent() {
    return myEditorTextField;
  }

  protected void doSetText(String text) {
    myEditorTextField.setText(text);
  }

  public String getText() {
    return myEditorTextField.getText();
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    final Editor editor = myEditorTextField.getEditor();
    return editor != null ? editor.getContentComponent() : null;
  }

  public void selectAll() {
    myEditorTextField.selectAll();
  }
}
