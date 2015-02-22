package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Creates methods for a builder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public class BuilderPreDefinedInnerClassMethodProcessor extends AbstractBuilderPreDefinedInnerClassProcessor {

  public BuilderPreDefinedInnerClassMethodProcessor() {
    this(Builder.class);
  }

  protected BuilderPreDefinedInnerClassMethodProcessor(@NotNull Class<? extends Annotation> builderClass) {
    super(builderClass, PsiMethod.class);
  }

  protected void generatePsiElements(@NotNull PsiClass psiParentClass, @NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final Collection<PsiField> tmpFields = builderHandler.createFields(psiParentClass);
    final PsiType psiBuilderType = PsiClassUtil.getTypeWithGenerics(psiParentClass);
    target.addAll(builderHandler.createConstructors(psiClass, psiAnnotation));
    target.addAll(builderHandler.createMethods(psiParentClass, null, psiClass, psiBuilderType, psiAnnotation, tmpFields));
  }

}
