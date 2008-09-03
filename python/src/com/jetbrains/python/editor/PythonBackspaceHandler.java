package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.BackspaceHandler;
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;

/**
 * @author yole
 */
public class PythonBackspaceHandler extends BackspaceHandlerDelegate {
  private LogicalPosition myTargetPosition;

  public void beforeCharDeleted(final char c, final PsiFile file, final Editor editor) {
    if (PythonFileType.INSTANCE != file.getFileType()) return;
    myTargetPosition = BackspaceHandler.getBackspaceUnindentPosition(file, editor);
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
