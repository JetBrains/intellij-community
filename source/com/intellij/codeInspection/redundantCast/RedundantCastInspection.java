/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.redundantCast;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RedundantCastInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.redundantCast.RedundantCastInspection");
  private LocalQuickFix myQuickFixAction;
  public static final String DISPLAY_NAME = "Redundant type cast";
  public static final String SHORT_NAME = "RedundantCast";

  public RedundantCastInspection() {
    myQuickFixAction = new AcceptSuggested();
  }

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    if (initializers == null || initializers.length == 0) return null;
    List<ProblemDescriptor> descriptors = new ArrayList<ProblemDescriptor>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] localDescriptions = getDescriptions(initializer, manager);
      if (localDescriptions != null) {
        descriptors.addAll(Arrays.asList(localDescriptions));
      }
    }
    if (descriptors.isEmpty()) return null;
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  public ProblemDescriptor[] checkField(PsiField field, InspectionManager manager, boolean isOnTheFly) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      return getDescriptions(initializer, manager);
    }
    return null;
  }

  public ProblemDescriptor[] checkMethod(PsiMethod psiMethod, InspectionManager manager, boolean isOnTheFly) {
    return getDescriptions(psiMethod, manager);
  }

  private ProblemDescriptor[] getDescriptions(PsiElement where, InspectionManager manager) {
    List<PsiTypeCastExpression> redundantCasts = RedundantCastUtil.getRedundantCastsInside(where);
    if (redundantCasts.isEmpty()) return null;
    ProblemDescriptor[] descriptions = new ProblemDescriptor[redundantCasts.size()];
    for (int i = 0; i < redundantCasts.size(); i++) {
      descriptions[i] = createDescription(redundantCasts.get(i), manager);
    }
    return descriptions;
  }

  private ProblemDescriptor createDescription(PsiTypeCastExpression cast, InspectionManager manager) {
    return manager.createProblemDescriptor(cast.getCastType(), "Casting <code>" + cast.getOperand().getText() + "</code> to " +
                                                               "<code>#ref</code> #loc is redundant", myQuickFixAction,
                                           ProblemHighlightType.LIKE_UNUSED_SYMBOL);
  }


  private static class AcceptSuggested implements LocalQuickFix {
    public String getName() {
      return "Remove Redundant Cast(s)";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      PsiElement castTypeElement = descriptor.getPsiElement();
      PsiTypeCastExpression cast = (PsiTypeCastExpression)castTypeElement.getParent();
      removeCast(cast);
    }

    public String getFamilyName() {
      return getName();
    }
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return "Verbose or redundant code constructs";
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  private static void removeCast(PsiTypeCastExpression castExpression) {
    if (castExpression == null) return;
    PsiExpression operand = castExpression.getOperand();
    if (operand == null) return;
    if (operand instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parExpr = (PsiParenthesizedExpression)operand;
      operand = parExpr.getExpression();
    }

    PsiElement toBeReplaced = castExpression;

    PsiElement parent = castExpression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      toBeReplaced = parent;
      parent = parent.getParent();
    }

    try {
      toBeReplaced.replace(operand);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
