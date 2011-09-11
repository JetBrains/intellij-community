package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface LombokFieldProcessor {

  public abstract <Psi extends PsiElement> boolean acceptAnnotation(@Nullable String qualifiedName, @NotNull Class<Psi> type);

  public abstract <Psi extends PsiElement> boolean process(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target);
}
