package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.lombok.processor.LombokProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface LombokFieldProcessor extends LombokProcessor {

  public abstract <Psi extends PsiElement> void process(@NotNull PsiField psiField, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target);
}
