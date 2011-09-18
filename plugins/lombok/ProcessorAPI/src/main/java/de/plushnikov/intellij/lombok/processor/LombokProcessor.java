package de.plushnikov.intellij.lombok.processor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public interface LombokProcessor {
  public abstract <Psi extends PsiElement> boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<Psi> type);

  /**
   * Workaround to get all of original Methods of the psiClass.
   * Normal call to psiClass.getMethods() is impossible because of incorrect cache implementation of IntelliJ Idea
   *
   * @param psiClass psiClass to collect all of methods from
   * @return all intern methods of the class
   */
  @NotNull
  public PsiMethod[] collectClassMethodsIntern(@NotNull PsiClass psiClass);

  /**
   * Workaround to get all of original Fields of the psiClass.
   * Normal call to psiClass.getFields() is impossible because of incorrect cache implementation of IntelliJ Idea
   *
   * @param psiClass psiClass to collect all of methods from
   * @return all intern fields of the class
   */
  @NotNull
  public PsiField[] collectClassFieldsIntern(@NotNull PsiClass psiClass);
}
