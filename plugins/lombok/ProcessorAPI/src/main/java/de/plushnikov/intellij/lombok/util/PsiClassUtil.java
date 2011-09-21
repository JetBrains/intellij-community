package de.plushnikov.intellij.lombok.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiClassImpl;
import org.jetbrains.annotations.NotNull;

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
}
