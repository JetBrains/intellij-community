package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;

public class PsiExpressionCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiExpressionCodeFragment {
  private PsiType myExpectedType;

  public PsiExpressionCodeFragmentImpl(Project project,
                                       boolean isPhysical,
                                       @NonNls String name,
                                       CharSequence text,
                                       final PsiType expectedType) {
    super(project, JavaElementType.EXPRESSION_TEXT, isPhysical, name, text);
    myExpectedType = expectedType;
  }

  public PsiExpression getExpression() {
    ChameleonTransforming.transformChildren(calcTreeElement());
    ASTNode exprChild = TreeUtil.findChild(calcTreeElement(), Constants.EXPRESSION_BIT_SET);
    if (exprChild == null) return null;
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(exprChild);
  }

  public PsiType getExpectedType() {
    return myExpectedType;
  }

  public void setExpectedType(PsiType type) {
    myExpectedType = type;
  }
}
