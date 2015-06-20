package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

public class NonSingularHandler extends AbstractSingularHandler {
  public static final String SETTER_PREFIX = "set";

  public void addBuilderField(@NotNull List<PsiField> fields, @NotNull PsiVariable psiVariable, @NotNull PsiClass innerClass, @NotNull AccessorsInfo accessorsInfo) {
    final String fieldName = accessorsInfo.removePrefix(psiVariable.getName());
    final LombokLightFieldBuilder fieldBuilder =
        new LombokLightFieldBuilder(psiVariable.getManager(), fieldName, getBuilderFieldType(psiVariable.getType(), psiVariable.getProject()))
            .withModifier(PsiModifier.PRIVATE)
            .withNavigationElement(psiVariable)
            .withContainingClass(innerClass);
    fields.add(fieldBuilder);
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiType, @NotNull Project project) {
    return psiType;
  }

  public void addBuilderMethod(@NotNull List<PsiMethod> methods, @NotNull PsiVariable psiVariable, @NotNull PsiClass innerClass, boolean fluentBuilder, PsiType returnType, PsiAnnotation singularAnnotation, @NotNull AccessorsInfo accessorsInfo) {
    final String psiFieldName = accessorsInfo.removePrefix(psiVariable.getName());

    methods.add(new LombokLightMethodBuilder(psiVariable.getManager(), createSetterName(psiFieldName, fluentBuilder))
        .withMethodReturnType(returnType)
        .withContainingClass(innerClass)
        .withParameter(psiFieldName, psiVariable.getType())
        .withNavigationElement(psiVariable)
        .withModifier(PsiModifier.PUBLIC)
        .withBody(PsiMethodUtil.createCodeBlockFromText(getAllMethodBody(psiFieldName, fluentBuilder), innerClass)));
  }

  @NotNull
  private String createSetterName(@NotNull String fieldName, boolean isFluent) {
    return isFluent ? fieldName : SETTER_PREFIX + StringUtil.capitalize(fieldName);
  }

  protected String getAllMethodBody(@NotNull String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "this.{0} = {0};{1}";
    return MessageFormat.format(codeBlockTemplate, psiFieldName, fluentBuilder ? "\nreturn this;" : "");
  }
}
