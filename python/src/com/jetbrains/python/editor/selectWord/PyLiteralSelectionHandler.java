package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStringLiteralExpression;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyLiteralSelectionHandler implements ExtendWordSelectionHandler {
  @Override
  public boolean canSelect(PsiElement e) {
    final ASTNode node = e.getNode();
    return node != null && PyTokenTypes.STRING_NODES.contains(node.getElementType());
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    final PyStringLiteralExpression literal = PsiTreeUtil.getParentOfType(e, PyStringLiteralExpression.class);
    if (literal != null) {
      for (TextRange stringRange : literal.getStringValueTextRanges()) {
        TextRange offsetRange = stringRange.shiftRight(literal.getTextRange().getStartOffset());
        if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
          return Collections.singletonList(offsetRange);
        }
      }
    }
    return Collections.emptyList();
  }
}
