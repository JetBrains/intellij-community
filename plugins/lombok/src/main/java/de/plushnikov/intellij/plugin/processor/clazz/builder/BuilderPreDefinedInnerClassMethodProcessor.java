package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
    final Collection<String> existedMethodNames = PsiClassUtil.collectClassMethodsIntern(psiBuilderClass).stream().map(PsiMethod::getName).collect(Collectors.toSet());

    final List<BuilderInfo> builderInfos = builderHandler.createBuilderInfos(psiAnnotation, psiParentClass, psiParentMethod, psiBuilderClass);

    //create constructor
    target.addAll(builderHandler.createConstructors(psiBuilderClass, psiAnnotation));

    // create builder methods
    builderInfos.stream()
      .filter(info -> info.notAlreadyExistingMethod(existedMethodNames))
      .map(BuilderInfo::renderBuilderMethods)
      .forEach(target::addAll);

    // create 'build' method
    final String buildMethodName = BuilderHandler.getBuildMethodName(psiAnnotation);
    if (!existedMethodNames.contains(buildMethodName)) {
      target.add(builderHandler.createBuildMethod(psiParentClass, psiParentMethod, psiBuilderClass, buildMethodName, builderInfos));
    }

    // create 'toString' method
    if (!existedMethodNames.contains(ToStringProcessor.METHOD_NAME)) {
      target.add(builderHandler.createToStringMethod(psiAnnotation, psiBuilderClass));
    }
  }

}
