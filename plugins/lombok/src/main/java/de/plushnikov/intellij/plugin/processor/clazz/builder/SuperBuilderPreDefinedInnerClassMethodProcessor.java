package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Creates methods for a builder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public class SuperBuilderPreDefinedInnerClassMethodProcessor extends AbstractSuperBuilderPreDefinedInnerClassProcessor {

  public SuperBuilderPreDefinedInnerClassMethodProcessor(@NotNull SuperBuilderHandler builderHandler) {
    super(builderHandler, PsiMethod.class, SuperBuilder.class);
  }

  @Override
  protected Collection<? extends PsiElement> generatePsiElementsOfBaseBuilderClass(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {
    return builderHandler.createAllMethodsOfBaseBuilder(psiParentClass, psiAnnotation, psiBuilderClass);
  }

  @Override
  protected Collection<? extends PsiElement> generatePsiElementsOfImplBuilderClass(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {
    return builderHandler.createAllMethodsOfImplBuilder(psiParentClass, psiAnnotation, psiBuilderClass);
  }

}
