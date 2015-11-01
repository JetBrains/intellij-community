package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface Processor {
  boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<? extends PsiElement> type);

  @NotNull
  String getSupportedAnnotation();

  @NotNull
  Class<? extends Annotation> getSupportedAnnotationClass();

  @NotNull
  Class<? extends PsiElement> getSupportedClass();

  @NotNull
  Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation);

  boolean isEnabled(@NotNull Project project);

  boolean canProduce(@NotNull Class<? extends PsiElement> type);

  @NotNull
  List<? super PsiElement> process(@NotNull PsiClass psiClass);

  LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation);

  LombokPsiElementUsage checkMethodUsage(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation);
}
