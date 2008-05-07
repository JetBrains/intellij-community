package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.DummyHolderFactory;

/**
 * @author ven
 */
public class ClsParsingUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsParsingUtil");
  public static PsiExpression createExpressionFromText(String exprText, PsiManager manager, ClsElementImpl parent) {
    PsiJavaFile dummyJavaFile = ((PsiElementFactoryImpl)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()).getDummyJavaFile(); // kind of hack - we need to resolve classes from java.lang
    final FileElement holderElement = DummyHolderFactory.createHolder(manager, dummyJavaFile).getTreeElement();
    CompositeElement _expr = ExpressionParsing.parseExpressionText(manager, exprText, 0, exprText.length(), holderElement.getCharTable());
    if (_expr == null){
      LOG.error("Could not parse expression:'" + exprText + "'");
      return null;
    }
    TreeUtil.addChildren(holderElement, _expr);
    PsiExpression expr = (PsiExpression)_expr.getPsi();
    if (expr instanceof PsiLiteralExpression){
      PsiLiteralExpression literal = (PsiLiteralExpression)expr;
      return new ClsLiteralExpressionImpl(parent, exprText, literal.getType(), literal.getValue());
    }
    else if (expr instanceof PsiPrefixExpression){
      PsiLiteralExpression operand = (PsiLiteralExpression)((PsiPrefixExpression)expr).getOperand();
      if (operand != null) {
        ClsLiteralExpressionImpl literalExpression = new ClsLiteralExpressionImpl(null, operand.getText(), operand.getType(), operand.getValue());
        ClsPrefixExpressionImpl prefixExpression = new ClsPrefixExpressionImpl(parent, literalExpression);
        literalExpression.setParent(prefixExpression);
        return prefixExpression;
      }
    }
    else if (expr instanceof PsiReferenceExpression){
      PsiReferenceExpression patternExpr = (PsiReferenceExpression)expr;
      return new ClsReferenceExpressionImpl(parent, patternExpr);
    }
    else{
      final PsiConstantEvaluationHelper constantEvaluationHelper =
            JavaPsiFacade.getInstance(manager.getProject()).getConstantEvaluationHelper();
      Object value = constantEvaluationHelper.computeConstantExpression(expr);
      if (value != null) {
        return new ClsLiteralExpressionImpl(parent, exprText, expr.getType(), value); //it seems ok to make literal expression with non-literal text
      }
    }
    LOG.error(expr.toString());
    return null;
  }
}
