package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Creates methods for a builder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public final class BuilderPreDefinedInnerClassMethodProcessor extends AbstractBuilderPreDefinedInnerClassProcessor {

  public BuilderPreDefinedInnerClassMethodProcessor() {
    super(PsiMethod.class, LombokClassNames.BUILDER);
  }

  @Override
  protected Collection<? extends PsiElement> generatePsiElements(@NotNull PsiClass psiParentClass,
                                                                 @Nullable PsiMethod psiParentMethod,
                                                                 @NotNull PsiAnnotation psiAnnotation,
                                                                 @NotNull PsiClass psiBuilderClass) {
    BuilderHandler builderHandler = getBuilderHandler();
    final List<BuilderInfo> builderInfos =
      builderHandler.createBuilderInfos(psiAnnotation, psiParentClass, psiParentMethod, psiBuilderClass);

    //create constructor
    final Collection<PsiMethod> result = new ArrayList<>(BuilderHandler.createConstructors(psiBuilderClass, psiAnnotation));

    final Map<String, List<List<PsiType>>> existingMethodsWithParameters =
      BuilderHandler.getExistingMethodsWithParameterTypes(psiBuilderClass);
    // create builder methods
    for (BuilderInfo info : builderInfos) {
      result.addAll(info.renderBuilderMethods(existingMethodsWithParameters));
    }

    // create 'build' method
    final String buildMethodName = BuilderHandler.getBuildMethodName(psiAnnotation);
    if (!BuilderHandler.matchMethodWithParams(existingMethodsWithParameters, buildMethodName, Collections.emptyList())) {
      result.add(
        builderHandler.createBuildMethod(psiAnnotation, psiParentClass, psiParentMethod, psiBuilderClass, buildMethodName, builderInfos));
    }

    // create 'toString' method
    if (!BuilderHandler.matchMethodWithParams(existingMethodsWithParameters, ToStringProcessor.TO_STRING_METHOD_NAME,
                                              Collections.emptyList())) {
      result.add(builderHandler.createToStringMethod(psiAnnotation, psiBuilderClass));
    }

    return result;
  }
}
