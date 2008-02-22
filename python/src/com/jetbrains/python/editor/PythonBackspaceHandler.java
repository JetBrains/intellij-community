package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.jetbrains.python.PythonFileType;

/**
 * @author yole
 */
public class PythonBackspaceHandler extends BackspaceHandlerDelegate {
  private LogicalPosition myTargetPosition;

  public void beforeCharDeleted(final char c, final PsiFile file, final Editor editor) {
    myTargetPosition = null;
    if (PythonFileType.INSTANCE != file.getFileType()) return;
    if (editor.getSelectionModel().hasSelection() || editor.getSelectionModel().hasBlockSelection()) return;

    LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
    if (caretPos.line == 1 || caretPos.column == 0) {
        return;
    }
    int lineStartOffset = editor.getDocument().getLineStartOffset(caretPos.line);
    int lineEndOffset = editor.getDocument().getLineEndOffset(caretPos.line);

    CharSequence charSeq = editor.getDocument().getCharsSequence();
    // smart backspace is activated only if all characters in the caret line
    // are whitespace characters
    for(int pos=lineStartOffset; pos<lineEndOffset; pos++) {
        if (charSeq.charAt(pos) != '\t' && charSeq.charAt(pos) != ' ' &&
                charSeq.charAt(pos) != '\n') {
            return;
        }
    }

    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(file.getProject());
    int column = caretPos.column - settings.getIndentSize(PythonFileType.INSTANCE);
    if (column < 0) column = 0;

    myTargetPosition = new LogicalPosition(caretPos.line, column);
  }

  public boolean charDeleted(final char c, final PsiFile file, final Editor editor) {
    if (myTargetPosition != null) {
      editor.getCaretModel().moveToLogicalPosition(myTargetPosition);
      myTargetPosition = null;
      return true;
    }
    return false;
  }
}
