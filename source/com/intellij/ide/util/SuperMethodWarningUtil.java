package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.usageView.UsageViewUtil;

public class SuperMethodWarningUtil {
  public static PsiMethod checkSuperMethod(final PsiMethod method, String actionString) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return method;

    PsiMethod superMethod = method.findDeepestSuperMethod();
    if (superMethod == null) return method;

    PsiClass containingClass = superMethod.getContainingClass();

    SuperMethodOrPointcutWarningDialog dialog =
      new SuperMethodOrPointcutWarningDialog(method.getProject(),
                                             UsageViewUtil.getDescriptiveName(method),
                                             false, containingClass.getQualifiedName(),
                                             actionString,
                                             containingClass.isInterface() || superMethod.hasModifierProperty(PsiModifier.ABSTRACT),
                                             containingClass.isInterface(),
                                             aClass.isInterface());
    dialog.show();

    if (dialog.getExitCode() == SuperMethodOrPointcutWarningDialog.OK_EXIT_CODE) return superMethod;
    if (dialog.getExitCode() == SuperMethodOrPointcutWarningDialog.NO_EXIT_CODE) return method;

    return null;
  }
}