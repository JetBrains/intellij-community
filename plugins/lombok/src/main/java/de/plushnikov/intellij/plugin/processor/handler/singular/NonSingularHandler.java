package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import de.plushnikov.intellij.plugin.thirdparty.CapitalizationStrategy;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

import static de.plushnikov.intellij.plugin.thirdparty.LombokAddNullAnnotations.createRelevantNonNullAnnotation;

class NonSingularHandler implements BuilderElementHandler {
  NonSingularHandler() {
  }

  @Override
  public Collection<PsiField> renderBuilderFields(@NotNull BuilderInfo info) {
    Collection<PsiField> result = new ArrayList<>();
    result.add(new LombokLightFieldBuilder(info.getManager(), info.renderFieldName(), info.getFieldType())
                 .withContainingClass(info.getBuilderClass())
                 .withModifier(PsiModifier.PRIVATE)
                 .withNavigationElement(info.getVariable()));
    if (info.hasBuilderDefaultAnnotation()) {
      result.add(new LombokLightFieldBuilder(info.getManager(), info.renderFieldDefaultSetName(), PsiTypes.booleanType())
                   .withContainingClass(info.getBuilderClass())
                   .withModifier(PsiModifier.PRIVATE)
                   .withNavigationElement(info.getVariable()));
    }
    return result;
  }

  @Override
  public Collection<PsiMethod> renderBuilderMethod(@NotNull BuilderInfo info, Map<String, List<List<PsiType>>> alreadyExistedMethods) {
    if (BuilderHandler.matchMethodWithParams(alreadyExistedMethods, calcBuilderMethodName(info), List.of(info.getFieldType()))) {
      return Collections.emptyList();
    }

    final String blockText = getAllMethodBody(info);
    final String methodName = calcBuilderMethodName(info);
    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(info.getManager(), methodName)
      .withContainingClass(info.getBuilderClass())
      .withMethodReturnType(info.getBuilderType())
      .withParameter(info.getFieldName(), info.getFieldType())
      .withNavigationElement(info.getVariable())
      .withModifier(info.getVisibilityModifier())
      .withAnnotations(info.getAnnotations())
      .withWriteAccess()
      .withBodyText(blockText);

    final LombokLightParameter setterParameter = methodBuilder.getParameterList().getParameter(0);
    if (info.getVariable() instanceof PsiField psiField) {
      if (null != setterParameter) {
        LombokCopyableAnnotations.copyCopyableAnnotations(psiField, setterParameter.getModifierList(),
                                                          LombokCopyableAnnotations.BASE_COPYABLE);
      }
      LombokCopyableAnnotations.copyCopyableAnnotations(psiField, methodBuilder.getModifierList(),
                                                        LombokCopyableAnnotations.COPY_TO_SETTER);
    }
    else {
      if (null != setterParameter) {
        LombokCopyableAnnotations.copyCopyableAnnotations(info.getVariable(), info.getBuilderClass(), setterParameter.getModifierList(),
                                                          LombokCopyableAnnotations.BASE_COPYABLE);
      }
      LombokCopyableAnnotations.copyCopyableAnnotations(info.getVariable(), info.getBuilderClass(), methodBuilder.getModifierList(),
                                                        LombokCopyableAnnotations.COPY_TO_SETTER);
    }

    createRelevantNonNullAnnotation(info.getNullAnnotationLibrary(), methodBuilder);

    return Collections.singleton(methodBuilder);
  }

  @Override
  public List<String> getBuilderMethodNames(@NotNull String fieldName, @NotNull String prefix, @Nullable PsiAnnotation singularAnnotation,
                                            CapitalizationStrategy capitalizationStrategy) {
    return Collections.singletonList(LombokUtils.buildAccessorName(prefix, fieldName, capitalizationStrategy));
  }

  @Override
  public String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    return psiFieldName;
  }

  private static String getAllMethodBody(@NotNull BuilderInfo info) {
    StringBuilder codeBlockTemplate = new StringBuilder("this.{0} = {1};\n");
    if (info.hasBuilderDefaultAnnotation()) {
      codeBlockTemplate.append("this.{2} = true;\n");
    }
    codeBlockTemplate.append("return {3};");
    return MessageFormat.format(codeBlockTemplate.toString(), info.renderFieldName(), info.getFieldName(),
                                info.renderFieldDefaultSetName(), info.getBuilderChainResult());
  }

  @Override
  public String renderBuildPrepare(@NotNull BuilderInfo info) {
    if (info.hasBuilderDefaultAnnotation()) {
      return MessageFormat.format(
        """
          {0} {1} = this.{1};
          if (!this.{2}) '{'
            {1} = {4}.{3}();
          '}'""",
        info.getFieldType().getCanonicalText(false),
        info.renderFieldName(), info.renderFieldDefaultSetName(), info.renderFieldDefaultProviderName(),
        info.getBuilderClass().getContainingClass().getName());
    }
    return "";
  }

  @Override
  public String renderBuildCall(@NotNull BuilderInfo info) {
    if (info.hasBuilderDefaultAnnotation()) {
      return info.renderFieldName();
    }
    else {
      return "this." + info.renderFieldName();
    }
  }
}
