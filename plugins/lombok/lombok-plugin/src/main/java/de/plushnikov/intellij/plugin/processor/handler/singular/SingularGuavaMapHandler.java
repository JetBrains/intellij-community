package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

public class SingularGuavaMapHandler extends SingularMapHandler {
  private static final String LOMBOK_KEY = "$key";
  private static final String LOMBOK_VALUE = "$value";

  private final String guavaQualifiedName;
  private final boolean sortedCollection;

  public SingularGuavaMapHandler(String guavaQualifiedName, boolean sortedCollection) {
    this.guavaQualifiedName = guavaQualifiedName;
    this.sortedCollection = sortedCollection;
  }

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
    return PsiTypeUtil.getCollectionClassType((PsiClassType) psiType, project, guavaQualifiedName + ".Builder");
  }

  protected void addOneMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {
    final PsiType[] psiTypes = PsiTypeUtil.extractTypeParameters(psiFieldType, methodBuilder.getManager());
    if (psiTypes.length == 2) {
      methodBuilder.withParameter(singularName + LOMBOK_KEY, psiTypes[0]);
      methodBuilder.withParameter(singularName + LOMBOK_VALUE, psiTypes[1]);
    }
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {2}.{3}; \n" +
        "this.{0}.put({1}" + LOMBOK_KEY + ", {1}" + LOMBOK_VALUE + ");{4}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, guavaQualifiedName,
        sortedCollection ? "naturalOrder()" : "builder()", fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getAllMethodBody(@NotNull String singularName, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {1}.{2}; \n"
        + "this.{0}.putAll({0});{3}";

    return MessageFormat.format(codeBlockTemplate, singularName, guavaQualifiedName,
        sortedCollection ? "naturalOrder()" : "builder()", fluentBuilder ? "\nreturn this;" : "");
  }
}
