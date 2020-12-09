package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
      result.add(new LombokLightFieldBuilder(info.getManager(), info.renderFieldDefaultSetName(), PsiType.BOOLEAN)
        .withContainingClass(info.getBuilderClass())
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(info.getVariable()));
    }
    return result;
  }

  @Override
  public Collection<PsiMethod> renderBuilderMethod(@NotNull BuilderInfo info) {
    final String blockText = getAllMethodBody(info);
    final String methodName = calcBuilderMethodName(info);
    final LombokLightMethodBuilder methodBuilder = new LombokLightMethodBuilder(info.getManager(), methodName)
      .withContainingClass(info.getBuilderClass())
      .withMethodReturnType(info.getBuilderType())
      .withParameter(info.getFieldName(), info.getFieldType())
      .withNavigationElement(info.getVariable())
      .withModifier(info.getVisibilityModifier())
      .withAnnotations(info.getAnnotations());
    methodBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(blockText, methodBuilder));
    return Collections.singleton(methodBuilder);
  }

  @Override
  public List<String> getBuilderMethodNames(@NotNull String newName, @Nullable PsiAnnotation singularAnnotation) {
    return Collections.singletonList(newName);
  }

  @Override
  public String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    return psiFieldName;
  }

  private String getAllMethodBody(@NotNull BuilderInfo info) {
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
        "{0} {1} = this.{1};\n" +
          "if (!this.{2}) '{'\n" +
          "  {1} = {4}.{3}();\n" +
          "'}'",
        info.getFieldType().getCanonicalText(false),
        info.renderFieldName(), info.renderFieldDefaultSetName(), info.renderFieldDefaultProviderName(),
        info.getBuilderClass().getContainingClass().getName());
    }
    return "";
  }
}
