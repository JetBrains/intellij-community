package com.intellij.ide.util;

import com.intellij.aspects.psi.PsiAspect;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.usageView.UsageViewUtil;

public class SuperMethodWarningUtil {
  public static PsiMethod checkSuperMethod(final PsiMethod method, String actionString) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return method;

    PsiMethod superMethod = PsiSuperMethodUtil.findDeepestSuperMethod(method);
    if (superMethod == null) return method;

    PsiClass containingClass = superMethod.getContainingClass();

    SuperMethodOrPointcutWarningDialog dialog = new SuperMethodOrPointcutWarningDialog(method.getProject(),
                                                                                       UsageViewUtil.getDescriptiveName(method),
        false, containingClass.getQualifiedName(),
        actionString,
        containingClass.isInterface() || superMethod.hasModifierProperty(PsiModifier.ABSTRACT),
        containingClass.isInterface(),
        aClass.isInterface(), containingClass instanceof PsiAspect);
    dialog.show();

    if (dialog.getExitCode() == SuperMethodOrPointcutWarningDialog.OK_EXIT_CODE) return superMethod;
    if (dialog.getExitCode() == SuperMethodOrPointcutWarningDialog.NO_EXIT_CODE) return method;

    return null;
  }

  public static PsiPointcutDef checkSuperPointcut(final PsiPointcutDef pointcut, String actionString) {
    PsiClass aClass = pointcut.getContainingClass();
    if (aClass == null) return pointcut;

    PsiPointcutDef superPointcut = PsiSuperMethodUtil.findDeepestSuperPointcut(pointcut);
    if (superPointcut == null) return pointcut;

    PsiClass containingClass = superPointcut.getContainingClass();

    SuperMethodOrPointcutWarningDialog dialog = new SuperMethodOrPointcutWarningDialog(pointcut.getProject(),
                                                                                       UsageViewUtil.getDescriptiveName(pointcut),
        true, containingClass.getQualifiedName(),
        actionString,
        containingClass.isInterface() || superPointcut.hasModifierProperty(PsiModifier.ABSTRACT),
        containingClass.isInterface(),
        aClass.isInterface(), containingClass instanceof PsiAspect);
    dialog.show();

    if (dialog.getExitCode() == SuperMethodOrPointcutWarningDialog.OK_EXIT_CODE) return superPointcut;
    if (dialog.getExitCode() == SuperMethodOrPointcutWarningDialog.NO_EXIT_CODE) return pointcut;

    return null;
  }
}