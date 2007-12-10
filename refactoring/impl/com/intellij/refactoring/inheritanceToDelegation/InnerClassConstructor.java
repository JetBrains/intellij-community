package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class InnerClassConstructor extends InnerClassMethod {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inheritanceToDelegation.InnerClassConstructor");
  public InnerClassConstructor(PsiMethod method) {
    super(method);
    LOG.assertTrue(method.isConstructor());
  }

  public void createMethod(PsiClass innerClass) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(innerClass.getProject()).getElementFactory();
    final PsiMethod constructor = factory.createConstructor();
    constructor.getNameIdentifier().replace(innerClass.getNameIdentifier());
    final PsiParameterList parameterList = myMethod.getParameterList();
    constructor.getParameterList().replace(parameterList);
    PsiExpressionStatement superCallStatement =
            (PsiExpressionStatement) factory.createStatementFromText("super();", null);

    PsiExpressionList arguments = ((PsiMethodCallExpression) superCallStatement.getExpression()).getArgumentList();
    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      arguments.add(factory.createExpressionFromText(parameters[i].getName(), null));
    }
    constructor.getBody().add(superCallStatement);
    innerClass.add(constructor);
  }
}
