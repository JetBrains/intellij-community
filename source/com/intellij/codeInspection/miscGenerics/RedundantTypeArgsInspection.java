package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class RedundantTypeArgsInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection");

  public RedundantTypeArgsInspection() {
    myQuickFixAction = new MyQuickFixAction();
  }

  private LocalQuickFix myQuickFixAction;

  public String getGroupDisplayName() {
    return "Local Code Analysis";
  }

  public String getDisplayName() {
    return "Redundant type arguments";
  }

  public String getShortName() {
    return "RedundantTypeArguments";
  }



  public ProblemDescriptor[] checkMethod(PsiMethod psiMethod, InspectionManager manager, boolean isOnTheFly) {
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      return getDescriptions(body, manager);
    }
    return null;
  }

  public ProblemDescriptor[] getDescriptions(PsiElement place, final InspectionManager inspectionManager) {
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    place.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {}

      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          checkCallExpression(expression.getMethodExpression(), typeArguments, expression, inspectionManager, problems);
        }
      }

      public void visitNewExpression(PsiNewExpression expression) {
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            checkCallExpression(classReference, typeArguments, expression, inspectionManager, problems);
          }
        }
      }

      private void checkCallExpression(final PsiJavaCodeReferenceElement reference,
                                       final PsiType[] typeArguments,
                                       PsiCallExpression expression,
                                       final InspectionManager inspectionManager, final List<ProblemDescriptor> problems) {

        final ResolveResult resolveResult = reference.advancedResolve(false);

        if (resolveResult.getElement() instanceof PsiMethod && resolveResult.isValidResult()) {
          PsiMethod method = (PsiMethod)resolveResult.getElement();
          final PsiTypeParameter[] typeParameters = method.getTypeParameterList().getTypeParameters();
          if (typeParameters.length == typeArguments.length) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            PsiResolveHelper resolveHelper = expression.getManager().getResolveHelper();
            for (int i = 0; i < typeParameters.length; i++) {
              PsiTypeParameter typeParameter = typeParameters[i];
              final PsiType inferedType = resolveHelper.inferTypeForMethodTypeParameter(typeParameter, parameters,
                                                                                        expression.getArgumentList().getExpressions(),
                                                                                        resolveResult.getSubstitutor(), expression);
              if (!typeArguments[i].equals(inferedType)) return;
            }

            final ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(expression.getTypeArgumentList(),
                                                                                           "Explicit type arguments can be inferred",
                                                                                           myQuickFixAction,
                                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            problems.add(descriptor);
          }
        }
      }

    });

    if (problems.isEmpty()) return null;
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private class MyQuickFixAction implements LocalQuickFix {
    public String getName() {
      return "Remove explicit type arguments";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      final PsiReferenceParameterList typeArgumentList = (PsiReferenceParameterList)descriptor.getPsiElement();
      try {
        typeArgumentList.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
