package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.lombok.processor.LombokProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface LombokFieldProcessor extends LombokProcessor {
  void process(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target);
}
