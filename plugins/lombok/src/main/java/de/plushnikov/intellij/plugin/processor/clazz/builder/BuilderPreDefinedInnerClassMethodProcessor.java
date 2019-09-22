package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
import lombok.experimental.Tolerate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates methods for a builder inner class if it is predefined.
 *
 * @author Michail Plushnikov
 */
public class BuilderPreDefinedInnerClassMethodProcessor extends AbstractBuilderPreDefinedInnerClassProcessor {

  public BuilderPreDefinedInnerClassMethodProcessor(@NotNull BuilderHandler builderHandler) {
    super(builderHandler, PsiMethod.class, Builder.class);
  }

  protected Collection<? extends PsiElement> generatePsiElements(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiParentMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {
    final Collection<PsiMethod> result = new ArrayList<>();

    final Collection<String> existedMethodNames = PsiClassUtil.collectClassMethodsIntern(psiBuilderClass).stream()
      .filter(psiMethod -> PsiAnnotationSearchUtil.isNotAnnotatedWith(psiMethod, Tolerate.class))
      .map(PsiMethod::getName).collect(Collectors.toSet());

    final List<BuilderInfo> builderInfos = builderHandler.createBuilderInfos(psiAnnotation, psiParentClass, psiParentMethod, psiBuilderClass);

    //create constructor
    result.addAll(builderHandler.createConstructors(psiBuilderClass, psiAnnotation));

    // create builder methods
    builderInfos.stream()
      .filter(info -> info.notAlreadyExistingMethod(existedMethodNames))
      .map(BuilderInfo::renderBuilderMethods)
      .forEach(result::addAll);

    // create 'build' method
    final String buildMethodName = builderHandler.getBuildMethodName(psiAnnotation);
    if (!existedMethodNames.contains(buildMethodName)) {
      result.add(builderHandler.createBuildMethod(psiAnnotation, psiParentClass, psiParentMethod, psiBuilderClass, buildMethodName, builderInfos));
    }

    // create 'toString' method
    if (!existedMethodNames.contains(ToStringProcessor.METHOD_NAME)) {
      result.add(builderHandler.createToStringMethod(psiAnnotation, psiBuilderClass));
    }

    return result;
  }

}
