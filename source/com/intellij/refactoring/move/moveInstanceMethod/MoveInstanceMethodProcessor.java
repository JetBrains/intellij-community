package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.FindUsagesCommand;

/**
 * @author ven
 */
public class MoveInstanceMethodProcessor extends BaseRefactoringProcessor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor");
  private PsiMethod myMethod;
  private PsiVariable myTargetVariable;
  private PsiClass myTargetClass;
  private String myOldVisibility;
  private String myNewVisibility;

  public MoveInstanceMethodProcessor(final Project project,
                                   final PsiMethod method,
                                   final PsiVariable targetVariable,
                                   final String newVisibility) {
    super(project);
    myMethod = method;
    myTargetVariable = targetVariable;
    LOG.assertTrue(myTargetVariable instanceof PsiParameter || myTargetVariable instanceof PsiField);
    LOG.assertTrue(myTargetVariable.getType() instanceof PsiClassType);
    final PsiType type = myTargetVariable.getType();
    LOG.assertTrue(type instanceof PsiClassType);
    final PsiClass targetClass = ((PsiClassType) type).resolve();
    myTargetClass = targetClass;
    myOldVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList ());
    myNewVisibility = newVisibility;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  protected UsageInfo[] findUsages() {
    return new UsageInfo[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  protected void refreshElements(PsiElement[] elements) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  protected void performRefactoring(UsageInfo[] usages) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  protected String getCommandName() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }
}
