package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface LombokClassProcessor {

  public abstract <Psi extends PsiElement> boolean acceptAnnotation(@Nullable String qualifiedName, @NotNull Class<Psi> type);

  public abstract <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target);
}
