package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.UnindentingInsertHandler;
import com.jetbrains.python.psi.PyFile;

/**
 * Handles typing of ":" which ends most enclosing constructs.
 * Adjusts indentation of closing parts like "else:", "finally:", etc.
 * User: dcheryasov
 * Date: Mar 2, 2010 3:50:10 PM
 */
public class ColonTypedHandler extends TypedHandlerDelegate {
  @Override
  public Result charTyped(char c, Project project, Editor editor, PsiFile file) {
    if (c == ':' && file instanceof PyFile) {
      UnindentingInsertHandler.unindentAsNeeded(project, editor, file);
    }
    return Result.CONTINUE;
  }
}
