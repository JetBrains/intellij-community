package de.plushnikov.intellij.lombok.processor;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface LombokProcessor {
  boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<? extends PsiElement> type);

  @NotNull
  String getSupportedAnnotation();

  Class<? extends Annotation> getSupportedAnnotationClass();

  Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation);


  boolean isEnabled(@NotNull Project project);

  boolean canProduce(@NotNull Class<? extends PsiElement> type);

  @NotNull
  List<? super PsiElement> process(@NotNull PsiClass psiClass);
}
