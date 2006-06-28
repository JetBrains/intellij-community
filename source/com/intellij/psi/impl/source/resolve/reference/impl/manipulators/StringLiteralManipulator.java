package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.resolve.reference.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class StringLiteralManipulator extends AbstractElementManipulator<PsiLiteralExpression> {
  public PsiLiteralExpression handleContentChange(PsiLiteralExpression expr, TextRange range, String newContent) throws IncorrectOperationException {
    if (!(expr.getValue() instanceof String)) throw new IncorrectOperationException("cannot handle content change");
    String oldText = expr.getText();
    newContent = StringUtil.escapeStringCharacters(newContent);
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    final PsiExpression newExpr = expr.getManager().getElementFactory().createExpressionFromText(newText, null);
    return (PsiLiteralExpression)expr.replace(newExpr);
  }
}
