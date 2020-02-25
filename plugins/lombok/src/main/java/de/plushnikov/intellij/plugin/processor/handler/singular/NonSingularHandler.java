package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class NonSingularHandler implements BuilderElementHandler {
  NonSingularHandler() {
  }

  public Collection<PsiField> renderBuilderFields(@NotNull BuilderInfo info) {
    return Collections.singleton(
      new LombokLightFieldBuilder(info.getManager(), info.getFieldName(), info.getFieldType())
        .withContainingClass(info.getBuilderClass())
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(info.getVariable()));
  }

  @Override
  public Collection<PsiMethod> renderBuilderMethod(@NotNull BuilderInfo info) {
    final String blockText = getAllMethodBody(info.getFieldName(), info.getBuilderChainResult());
    final String methodName = LombokUtils.buildAccessorName(info.getSetterPrefix(), info.getFieldName());
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

  public List<String> getBuilderMethodNames(@NotNull String newName, @Nullable PsiAnnotation singularAnnotation) {
    return Collections.singletonList(newName);
  }

  @Override
  public String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    return psiFieldName;
  }

  private String getAllMethodBody(@NotNull String psiFieldName, @NotNull String builderChainResult) {
    final String codeBlockTemplate = "this.{0} = {0};\nreturn {1};";
    return MessageFormat.format(codeBlockTemplate, psiFieldName, builderChainResult);
  }
}
