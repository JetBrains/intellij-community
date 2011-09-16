package de.plushnikov.intellij.lombok.processor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public interface LombokProcessor {
  public abstract <Psi extends PsiElement> boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<Psi> type);
}
