package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.lombok.processor.LombokProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface LombokClassProcessor extends LombokProcessor {

  public abstract <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target);
}
