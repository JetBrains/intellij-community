package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

public class SingularGuavaCollectionHandler extends SingularCollectionHandler {

  private final String guavaQualifiedName;
  private final boolean sortedCollection;

  public SingularGuavaCollectionHandler(String guavaQualifiedName, boolean sortedCollection) {
    this.guavaQualifiedName = guavaQualifiedName;
    this.sortedCollection = sortedCollection;
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiType, @NotNull Project project) {
    return PsiTypeUtil.getCollectionClassType((PsiClassType) psiType, project, guavaQualifiedName + ".Builder");
  }

  protected void addAllMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {
    final Project project = methodBuilder.getProject();

    final PsiType collectionType = PsiTypeUtil.getCollectionClassType((PsiClassType) psiFieldType, project, CommonClassNames.JAVA_LANG_ITERABLE);

    methodBuilder.withParameter(singularName, collectionType);
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, @NotNull PsiType[] psiParameterTypes, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {2}.{3}; \n" +
        "this.{0}.add({1});{4}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, guavaQualifiedName,
        sortedCollection ? "naturalOrder()" : "builder()", fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getAllMethodBody(@NotNull String singularName, @NotNull PsiType[] psiParameterTypes, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {1}.{2}; \n"
        + "this.{0}.addAll({0});{3}";

    return MessageFormat.format(codeBlockTemplate, singularName, guavaQualifiedName,
        sortedCollection ? "naturalOrder()" : "builder()", fluentBuilder ? "\nreturn this;" : "");
  }
}
