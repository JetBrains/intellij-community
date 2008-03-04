package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.psi.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyStatementSelectionHandler extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(final PsiElement e) {
    return e instanceof PyStringLiteralExpression || e instanceof PyCallExpression || e instanceof PyStatement ||
           e instanceof PyStatementList || e instanceof PyFunction || e instanceof PyClass;
  }

  public List<TextRange> select(final PsiElement e, final CharSequence editorText, final int cursorOffset, final Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();
    PsiElement endElement = e;
    while(endElement.getLastChild() != null) {
      endElement = endElement.getLastChild();
    }
    if (endElement instanceof PsiWhiteSpace) {
      final PsiElement prevSibling = endElement.getPrevSibling();
      if (prevSibling != null) {
        endElement = prevSibling;
      }
    }
    result.addAll(expandToWholeLine(editorText, new TextRange(e.getTextRange().getStartOffset(),
                                                              endElement.getTextRange().getEndOffset())));

    return result;
  }
}
