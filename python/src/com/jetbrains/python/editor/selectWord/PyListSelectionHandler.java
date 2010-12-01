package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyListLiteralExpression;

import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 *
 * Handler to select list literal expression
 */
public class PyListSelectionHandler implements ExtendWordSelectionHandler {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PyListLiteralExpression;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    TextRange stringRange = e.getTextRange();
    TextRange offsetRange = new TextRange(stringRange.getStartOffset() + 1, stringRange.getEndOffset() -1 );
    if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
      return Collections.singletonList(offsetRange);
    }
    return Collections.emptyList();
  }
}
