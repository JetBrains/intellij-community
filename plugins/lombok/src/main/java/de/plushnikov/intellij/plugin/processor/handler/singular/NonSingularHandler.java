package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class NonSingularHandler implements BuilderElementHandler {
  private static final String SETTER_PREFIX = "set";

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
    return Collections.singleton(new LombokLightMethodBuilder(info.getManager(), createSetterName(info.getFieldName(), info.isFluentBuilder()))
      .withContainingClass(info.getBuilderClass())
      .withMethodReturnType(info.isChainBuilder() ? info.getBuilderType() : PsiType.VOID)
      .withParameter(info.getFieldName(), info.getFieldType())
      .withNavigationElement(info.getVariable())
      .withModifier(PsiModifier.PUBLIC)
      .withAnnotations(info.getAnnotations())
      .withBody(createCodeBlock(info.getBuilderClass(), info.isFluentBuilder(), info.getFieldName())));
  }

  public List<String> getBuilderMethodNames(@NotNull String newName, @Nullable PsiAnnotation singularAnnotation) {
    return Collections.singletonList(createSetterName(newName, true));
  }

  @NotNull
  private String createSetterName(@NotNull String fieldName, boolean isFluent) {
    return isFluent ? fieldName : SETTER_PREFIX + StringUtil.capitalize(fieldName);
  }

  @NotNull
  private PsiCodeBlock createCodeBlock(@NotNull PsiClass innerClass, boolean fluentBuilder, String psiFieldName) {
    final String blockText = getAllMethodBody(psiFieldName, fluentBuilder);
    return PsiMethodUtil.createCodeBlockFromText(blockText, innerClass);
  }

  @Override
  public String createSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    return psiFieldName;
  }

  private String getAllMethodBody(@NotNull String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "this.{0} = {0};{1}";
    return MessageFormat.format(codeBlockTemplate, psiFieldName, fluentBuilder ? "\nreturn this;" : "");
  }
}
