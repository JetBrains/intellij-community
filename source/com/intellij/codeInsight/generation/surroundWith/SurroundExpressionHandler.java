
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;

public interface SurroundExpressionHandler{
  boolean isApplicable(PsiExpression expr);

  /**
   * @return range to select/to position the caret
   */
  TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException;
}