package com.intellij.usages.impl.rules;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author yole
 */
public class JavaUsageTypeProvider implements UsageTypeProvider {
  public UsageType getUsageType(final PsiElement element) {
    UsageType classUsageType = getClassUsageType(element);
    if (classUsageType != null) return classUsageType;

    UsageType methodUsageType = getMethodUsageType(element);
    if (methodUsageType != null) return methodUsageType;

    if (element instanceof PsiLiteralExpression) {return UsageType.LITERAL_USAGE; }

    return null;
  }

  @Nullable
  private static UsageType getMethodUsageType(PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      final PsiMethod containerMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (containerMethod != null) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        final PsiElement p = referenceExpression.getParent();
        if (p instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)p;
          final PsiMethod calledMethod = callExpression.resolveMethod();
          if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
            if (haveCommonSuperMethod(containerMethod, calledMethod)) {
              boolean parametersDelegated = parametersDelegated(containerMethod, callExpression);

              if (qualifier instanceof PsiSuperExpression) {
                return parametersDelegated ? UsageType.DELEGATE_TO_SUPER : UsageType.DELEGATE_TO_SUPER_PARAMETERS_CHANGED;
              }
              else {
                return parametersDelegated ? UsageType.DELEGATE_TO_ANOTHER_INSTANCE : UsageType.DELEGATE_TO_ANOTHER_INSTANCE_PARAMETERS_CHANGED;
              }
            }
          }
          else if (calledMethod == containerMethod) {
            return UsageType.RECURSION;
          }
        }
      }
    }

    return null;
  }

  private static boolean parametersDelegated(final PsiMethod method, final PsiMethodCallExpression call) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiExpression[] arguments = call.getArgumentList().getExpressions();
    if (parameters.length != arguments.length) return false;

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiExpression argument = arguments[i];

      if (!(argument instanceof PsiReferenceExpression)) return false;
      if (!((PsiReferenceExpression)argument).isReferenceTo(parameter)) return false;
    }

    for (PsiParameter parameter : parameters) {
      if (PsiUtil.isAssigned(parameter)) return false;
    }

    return true;
  }

  private static boolean haveCommonSuperMethod(PsiMethod m1, PsiMethod m2) {
    HashSet<PsiMethod> s1 = new HashSet<PsiMethod>(Arrays.asList(m1.findDeepestSuperMethods()));
    s1.add(m1);

    HashSet<PsiMethod> s2 = new HashSet<PsiMethod>(Arrays.asList(m2.findDeepestSuperMethods()));
    s2.add(m2);

    s1.retainAll(s2);
    return !s1.isEmpty();
  }

  @Nullable
  private static UsageType getClassUsageType(PsiElement element) {

    if (PsiTreeUtil.getParentOfType(element, PsiImportStatement.class, false) != null) return UsageType.CLASS_IMPORT;
    PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(element, PsiReferenceList.class);
    if (referenceList != null) {
      if (referenceList.getParent() instanceof PsiClass) return UsageType.CLASS_EXTENDS_IMPLEMENTS_LIST;
      if (referenceList.getParent() instanceof PsiMethod) return UsageType.CLASS_METHOD_THROWS_LIST;
    }

    PsiTypeCastExpression castExpression = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);
    if (castExpression != null) {
      if (PsiTreeUtil.isAncestor(castExpression.getCastType(), element, true)) return UsageType.CLASS_CAST_TO;
    }

    PsiInstanceOfExpression instanceOfExpression = PsiTreeUtil.getParentOfType(element, PsiInstanceOfExpression.class);
    if (instanceOfExpression != null) {
      if (PsiTreeUtil.isAncestor(instanceOfExpression.getCheckType(), element, true)) return UsageType.CLASS_INSTANCE_OF;
    }

    if (PsiTreeUtil.getParentOfType(element, PsiClassObjectAccessExpression.class) != null) return UsageType.CLASS_CLASS_OBJECT_ACCESS;

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression expression = (PsiReferenceExpression)element;
      if (expression.resolve() instanceof PsiClass) {
        return UsageType.CLASS_STATIC_MEMBER_ACCESS;
      }
    }

    final PsiParameter psiParameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (psiParameter != null) {
      final PsiElement scope = psiParameter.getDeclarationScope();
      if (scope instanceof PsiMethod) return UsageType.CLASS_METHOD_PARAMETER_DECLARATION;
      if (scope instanceof PsiCatchSection) return UsageType.CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION;
      if (scope instanceof PsiForeachStatement) return UsageType.CLASS_LOCAL_VAR_DECLARATION;
      return null;
    }

    PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (psiField != null) {
      if (PsiTreeUtil.isAncestor(psiField.getTypeElement(), element, true)) return UsageType.CLASS_FIELD_DECLARATION;
    }

    PsiLocalVariable psiLocalVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (psiLocalVar != null) {
      if (PsiTreeUtil.isAncestor(psiLocalVar.getTypeElement(), element, true)) return UsageType.CLASS_LOCAL_VAR_DECLARATION;
    }

    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (psiMethod != null) {
      final PsiTypeElement retType = psiMethod.getReturnTypeElement();
      if (retType != null && PsiTreeUtil.isAncestor(retType, element, true)) return UsageType.CLASS_METHOD_RETURN_TYPE;
    }

    final PsiNewExpression psiNewExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    if (psiNewExpression != null) {
      final PsiJavaCodeReferenceElement classReference = psiNewExpression.getClassReference();
      if (classReference != null && PsiTreeUtil.isAncestor(classReference, element, false)) return UsageType.CLASS_NEW_OPERATOR;
    }

    return null;
  }
}
