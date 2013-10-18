package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PyStringLiteralLexer;
import com.jetbrains.python.psi.PyStringLiteralExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyLiteralSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(PsiElement e) {
    final ASTNode node = e.getNode();
    return node != null && PyTokenTypes.STRING_NODES.contains(node.getElementType());
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    final PyStringLiteralExpression literal = PsiTreeUtil.getParentOfType(e, PyStringLiteralExpression.class);
    if (literal != null) {
      List<TextRange> ranges = literal.getStringValueTextRanges();
      List<ASTNode> nodes = literal.getStringNodes();
      for (int i = 0; i < ranges.size(); i++) {
        TextRange stringRange = ranges.get(i);
        TextRange offsetRange = stringRange.shiftRight(literal.getTextRange().getStartOffset());
        if (offsetRange.contains(cursorOffset) && offsetRange.getLength() > 1) {
          List<TextRange> result = new ArrayList<TextRange>();
          SelectWordUtil.addWordHonoringEscapeSequences(editorText, nodes.get(i).getTextRange(), cursorOffset,
                                                        new PyStringLiteralLexer(nodes.get(i).getElementType()),
                                                        result);
          result.add(offsetRange);
          return result;
        }
      }
    }
    return Collections.emptyList();
  }
}
