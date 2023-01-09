package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
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

  public BuilderPreDefinedInnerClassMethodProcessor() {
    super(PsiMethod.class, LombokClassNames.BUILDER);
  }



  @Override
  protected Collection<? extends PsiElement> generatePsiElements(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiParentMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass) {

    final Collection<String> existedMethodNames = PsiClassUtil.collectClassMethodsIntern(psiBuilderClass).stream()
      .filter(psiMethod -> PsiAnnotationSearchUtil.isNotAnnotatedWith(psiMethod, LombokClassNames.TOLERATE))
      .map(PsiMethod::getName).collect(Collectors.toSet());

    BuilderHandler builderHandler = getBuilderHandler();
    final List<BuilderInfo> builderInfos = builderHandler.createBuilderInfos(psiAnnotation, psiParentClass, psiParentMethod, psiBuilderClass);

    //create constructor
    final Collection<PsiMethod> result = new ArrayList<>(BuilderHandler.createConstructors(psiBuilderClass, psiAnnotation));

    // create builder methods
    builderInfos.stream()
      .filter(info -> info.notAlreadyExistingMethod(existedMethodNames))
      .map(BuilderInfo::renderBuilderMethods)
      .forEach(result::addAll);

    // create 'build' method
    final String buildMethodName = BuilderHandler.getBuildMethodName(psiAnnotation);
    if (!existedMethodNames.contains(buildMethodName)) {
      result.add(builderHandler.createBuildMethod(psiAnnotation, psiParentClass, psiParentMethod, psiBuilderClass, buildMethodName, builderInfos));
    }

    // create 'toString' method
    if (!existedMethodNames.contains(ToStringProcessor.TO_STRING_METHOD_NAME)) {
      result.add(builderHandler.createToStringMethod(psiAnnotation, psiBuilderClass));
    }

    return result;
  }

}
