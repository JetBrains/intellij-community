package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;


/**
 * @author ven
 */
public class AddAssertStatementFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.AddAssertStatementFix");
  private PsiExpression myExpressionToAssert;

  public String getName() {
    return "Assert '" + myExpressionToAssert.getText() + "'";
  }

  public AddAssertStatementFix(PsiExpression expressionToAssert) {
    myExpressionToAssert = expressionToAssert;
    LOG.assertTrue(PsiType.BOOLEAN.equals(myExpressionToAssert.getType()));
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiStatement anchorStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    LOG.assertTrue(anchorStatement != null);

    String text = "assert c;";
    PsiAssertStatement assertStatement;
    try {
      assertStatement = (PsiAssertStatement)element.getManager().getElementFactory().createStatementFromText(text, null);
      assertStatement.getAssertCondition().replace(myExpressionToAssert);
      anchorStatement.getParent().addBefore(assertStatement, anchorStatement);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public String getFamilyName() {
    return "Assert";
  }
}
