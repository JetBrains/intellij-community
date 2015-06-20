package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

public class NonSingularHandler extends AbstractSingularHandler {
  public static final String SETTER_PREFIX = "set";

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiType, @NotNull Project project) {
    return psiType;
  }

  public void addBuilderMethod(@NotNull List<PsiMethod> methods, @NotNull PsiField psiField, @NotNull PsiClass innerClass, boolean fluentBuilder, PsiType returnType, PsiAnnotation singularAnnotation) {
    final String psiFieldName = psiField.getName();

    methods.add(new LombokLightMethodBuilder(psiField.getManager(), createSetterName(psiFieldName, fluentBuilder))
        .withMethodReturnType(returnType)
        .withContainingClass(innerClass)
        .withParameter(psiFieldName, psiField.getType())
        .withNavigationElement(psiField)
        .withModifier(PsiModifier.PUBLIC)
        .withBody(PsiMethodUtil.createCodeBlockFromText(getAllMethodBody(psiFieldName, fluentBuilder), innerClass)));
  }

  @NotNull
  private String createSetterName(@NotNull String fieldName, boolean isFluent) {
    return isFluent ? fieldName : SETTER_PREFIX + StringUtil.capitalize(fieldName);
  }

  protected String getAllMethodBody(String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "this.{0} = {0};{1}";
    return MessageFormat.format(codeBlockTemplate, psiFieldName, fluentBuilder ? "\nreturn this;" : "");
  }
}
