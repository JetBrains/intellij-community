package com.jetbrains.python.console.completion;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;

/**
 * @author oleg
 * @date 10/26/10
 */
public class PythonConsoleAutopopupBlockingHandler extends TypedHandlerDelegate {

  public static final Key<Object> REPL_KEY = new Key<Object>("python.repl.console.editor");

  @Override
  public Result checkAutoPopup(final char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (editor.getUserData(REPL_KEY) != null){
      return Result.DEFAULT;
    }
    return Result.CONTINUE;
  }
}
