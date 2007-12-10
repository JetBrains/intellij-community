package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;

/**
 * @author ven
 */
public class ClsParsingUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsParsingUtil");
  public static PsiExpression createExpressionFromText(String exprText, PsiManager manager, ClsElementImpl parent) {
    PsiJavaFile dummyJavaFile = ((PsiElementFactoryImpl)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()).getDummyJavaFile(); // kind of hack - we need to resolve classes from java.lang
    final FileElement holderElement = new DummyHolder(manager, dummyJavaFile).getTreeElement();
    CompositeElement _expr = ExpressionParsing.parseExpressionText(manager, exprText, 0, exprText.length(), holderElement.getCharTable());
    if (_expr == null){
      LOG.error("Could not parse expression:'" + exprText + "'");
      return null;
    }
    TreeUtil.addChildren(holderElement, _expr);
    if (_expr instanceof PsiLiteralExpression){
      PsiLiteralExpression expr = (PsiLiteralExpression)_expr;
      return new ClsLiteralExpressionImpl(parent, exprText, expr.getType(), expr.getValue());
    }
    else if (_expr instanceof PsiPrefixExpression){
      PsiLiteralExpression operand = (PsiLiteralExpression)((PsiPrefixExpression)_expr).getOperand();
      ClsLiteralExpressionImpl literalExpression = new ClsLiteralExpressionImpl(null, operand.getText(), operand.getType(), operand.getValue());
      ClsPrefixExpressionImpl prefixExpression = new ClsPrefixExpressionImpl(parent, literalExpression);
      literalExpression.setParent(prefixExpression);
      return prefixExpression;
    }
    else if (_expr instanceof PsiReferenceExpression){
      PsiReferenceExpression patternExpr = (PsiReferenceExpression)SourceTreeToPsiMap.treeElementToPsi(_expr);
      return new ClsReferenceExpressionImpl(parent, patternExpr);
    }
    else{
      LOG.error(_expr.toString());
      return null;
    }
  }
}
