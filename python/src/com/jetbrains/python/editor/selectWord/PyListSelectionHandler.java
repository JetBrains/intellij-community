package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyParameterList;

import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 *
 * Handler to select list contents without parentheses 
 */
public class PyListSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PyListLiteralExpression || e instanceof PyParameterList || e instanceof PyArgumentList;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    TextRange stringRange = e.getTextRange();
    PsiElement firstChild = e.getFirstChild().getNextSibling();
    int startShift = 1;
    if (firstChild instanceof PsiWhiteSpace)
      startShift += firstChild.getTextLength();
    PsiElement lastChild = e.getLastChild().getPrevSibling();
    int endShift = 1;
    if (lastChild instanceof PsiWhiteSpace)
      endShift += lastChild.getTextLength();

    TextRange offsetRange = new TextRange(stringRange.getStartOffset() + startShift, stringRange.getEndOffset() - endShift );
    if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
      return Collections.singletonList(offsetRange);
    }
    return Collections.emptyList();
  }
}
