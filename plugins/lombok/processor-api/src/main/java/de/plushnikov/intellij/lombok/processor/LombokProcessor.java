package de.plushnikov.intellij.lombok.processor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Plushnikov Michail
 */
public interface LombokProcessor {
  <Psi extends PsiElement> boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<Psi> type);

  @NotNull
  String getSupportedAnnotation();

  Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation);
}
