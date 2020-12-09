package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Creates methods for a builder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public class SuperBuilderPreDefinedInnerClassMethodProcessor extends AbstractSuperBuilderPreDefinedInnerClassProcessor {

  public SuperBuilderPreDefinedInnerClassMethodProcessor() {
    super(PsiMethod.class, LombokClassNames.SUPER_BUILDER);
  }

  @Override
  protected SuperBuilderHandler getBuilderHandler() {
    return ApplicationManager.getApplication().getService(SuperBuilderHandler.class);
  }

  @Override
  protected Collection<? extends PsiElement> generatePsiElementsOfBaseBuilderClass(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {
    return getBuilderHandler().createAllMethodsOfBaseBuilder(psiParentClass, psiAnnotation, psiBuilderClass);
  }

  @Override
  protected Collection<? extends PsiElement> generatePsiElementsOfImplBuilderClass(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {
    return getBuilderHandler().createAllMethodsOfImplBuilder(psiParentClass, psiAnnotation, psiBuilderClass);
  }

}
