package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.lombok.processor.LombokProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface LombokClassProcessor extends LombokProcessor {
  void process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target);
}
