package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Creates methods for a builder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public class BuilderPreDefinedInnerClassMethodProcessor extends AbstractBuilderPreDefinedInnerClassProcessor {

  @SuppressWarnings({"deprecation", "unchecked"})
  public BuilderPreDefinedInnerClassMethodProcessor(@NotNull BuilderHandler builderHandler) {
    super(builderHandler, PsiMethod.class, Builder.class, lombok.experimental.Builder.class);
  }

  protected void generatePsiElements(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiParentMethod, @NotNull PsiClass psiBuilderClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiType psiBuilderType = builderHandler.getBuilderType(psiParentClass, psiParentMethod);
    if (null == psiParentMethod) {
      final Collection<PsiField> builderFields = builderHandler.getBuilderFields(psiParentClass, Collections.<PsiField>emptySet(), AccessorsInfo.EMPTY);
      target.addAll(builderHandler.createConstructors(psiBuilderClass, psiAnnotation));
      target.addAll(builderHandler.createMethods(psiParentClass, null, psiBuilderClass, psiBuilderType, psiAnnotation, builderFields, BuilderHandler.getBuilderSubstitutor(psiParentClass, psiBuilderClass)));
    } else {
      final Collection<PsiParameter> builderParameters = builderHandler.getBuilderParameters(psiParentMethod, Collections.<PsiField>emptySet());
      target.addAll(builderHandler.createConstructors(psiBuilderClass, psiAnnotation));
      target.addAll(builderHandler.createMethods(psiParentClass, psiParentMethod, psiBuilderClass, psiBuilderType, psiAnnotation, builderParameters, BuilderHandler.getBuilderSubstitutor(psiParentMethod, psiBuilderClass)));
    }
  }

}
