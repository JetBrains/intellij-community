package de.plushnikov.intellij.lombok.util;

import java.util.ArrayList;
import java.util.Collection;

import org.jetbrains.annotations.NotNull;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiClassImpl;

/**
 * @author Plushnikov Michail
 */
public class PsiClassUtil {
  /**
   * Workaround to get all of original Methods of the psiClass.
   * Normal call to psiClass.getMethods() in PsiAugmentProvider is impossible because of incorrect cache implementation of IntelliJ Idea
   *
   * @param psiClass psiClass to collect all of methods from
   * @return all intern methods of the class
   */
  @NotNull
  public static PsiMethod[] collectClassMethodsIntern(@NotNull PsiClass psiClass) {
    return ((PsiClassImpl) psiClass).getStubOrPsiChildren(Constants.METHOD_BIT_SET, PsiMethod.ARRAY_FACTORY);
  }

  @NotNull
  public static PsiMethod[] collectClassConstructorIntern(@NotNull PsiClass psiClass) {
    final PsiMethod[] psiMethods = collectClassMethodsIntern(psiClass);

    Collection<PsiMethod> classConstructors = new ArrayList<PsiMethod>(3);
    for (PsiMethod psiMethod : psiMethods) {
      if (psiMethod.isConstructor()) {
        classConstructors.add(psiMethod);
      }
    }
    return classConstructors.toArray(new PsiMethod[classConstructors.size()]);
  }

  @NotNull
  public static PsiMethod[] collectClassStaticMethodsIntern(@NotNull PsiClass psiClass) {
    final PsiMethod[] psiMethods = collectClassMethodsIntern(psiClass);

    Collection<PsiMethod> staticMethods = new ArrayList<PsiMethod>(5);
    for (PsiMethod psiMethod : psiMethods) {
      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        staticMethods.add(psiMethod);
      }
    }
    return staticMethods.toArray(new PsiMethod[staticMethods.size()]);
  }

  /**
   * Workaround to get all of original Fields of the psiClass.
   * Normal call to psiClass.getFields() in PsiAugmentProvider is impossible because of incorrect cache implementation of IntelliJ Idea
   *
   * @param psiClass psiClass to collect all of methods from
   * @return all intern fields of the class
   */
  @NotNull
  public static PsiField[] collectClassFieldsIntern(@NotNull PsiClass psiClass) {
    return ((PsiClassImpl) psiClass).getStubOrPsiChildren(Constants.FIELD_BIT_SET, PsiField.ARRAY_FACTORY);
  }

  public static PsiClassType getClassType(@NotNull PsiClass psiClass) {
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
  }
}
