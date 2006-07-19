package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SuperMethodWarningUtil {
  private SuperMethodWarningUtil() {}

  @NotNull
  public static PsiMethod[] checkSuperMethods(final PsiMethod method, String actionString) {
    return checkSuperMethods(method, actionString, null);
  }

  @NotNull
  public static PsiMethod[] checkSuperMethods(final PsiMethod method, String actionString, Set<PsiElement> ignore) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};

    final Collection<PsiMethod> superMethods = DeepestSuperMethodsSearch.search(method).findAll();
    if (ignore != null) {
      superMethods.removeAll(ignore);
    }

    if (superMethods.isEmpty()) return new PsiMethod[]{method};


    Set<String> superClasses = new HashSet<String>();
    boolean superAbstract = false;
    boolean parentInterface = false;
    for (final PsiMethod superMethod : superMethods) {
      final PsiClass containingClass = superMethod.getContainingClass();
      superClasses.add(containingClass.getQualifiedName());
      final boolean isInterface = containingClass.isInterface();
      superAbstract |= isInterface || superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
      parentInterface |= isInterface;
    }

    SuperMethodWarningDialog dialog =
        new SuperMethodWarningDialog(
            method.getProject(),
            UsageViewUtil.getDescriptiveName(method),
            actionString,
            superAbstract,
            parentInterface,
            aClass.isInterface(),
            superClasses.toArray(new String[superClasses.size()])
    );
    dialog.show();

    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      return superMethods.toArray(new PsiMethod[superMethods.size()]);
    }
    if (dialog.getExitCode() == SuperMethodWarningDialog.NO_EXIT_CODE) {
      return new PsiMethod[]{method};
    }

    return PsiMethod.EMPTY_ARRAY;
  }


  public static PsiMethod checkSuperMethod(final PsiMethod method, String actionString) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return method;

    PsiMethod superMethod = method.findDeepestSuperMethod();
    if (superMethod == null) return method;

    PsiClass containingClass = superMethod.getContainingClass();

    SuperMethodWarningDialog dialog =
        new SuperMethodWarningDialog(
            method.getProject(),
            UsageViewUtil.getDescriptiveName(method), actionString, containingClass.isInterface() || superMethod.hasModifierProperty(PsiModifier.ABSTRACT),
            containingClass.isInterface(), aClass.isInterface(), containingClass.getQualifiedName()
        );
    dialog.show();

    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) return superMethod;
    if (dialog.getExitCode() == SuperMethodWarningDialog.NO_EXIT_CODE) return method;

    return null;
  }
}