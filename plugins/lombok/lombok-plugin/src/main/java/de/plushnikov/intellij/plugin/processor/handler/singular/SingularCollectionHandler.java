package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

public class SingularCollectionHandler extends AbstractSingularHandler {

  protected void addOneMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {
    final PsiType[] psiTypes = PsiTypeUtil.extractTypeParameters(psiFieldType, methodBuilder.getManager());
    if (psiTypes.length == 1) {
      methodBuilder.withParameter(singularName, psiTypes[0]);
    }
  }

  protected void addAllMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {
    final Project project = methodBuilder.getProject();

    final PsiType collectionType = PsiTypeUtil.getCollectionClassType((PsiClassType) psiFieldType, project, CommonClassNames.JAVA_UTIL_COLLECTION);

    methodBuilder.withParameter(singularName, collectionType);
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = new java.util.ArrayList(); \n" +
        "this.{0}.add({1});{2}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getAllMethodBody(@NotNull String singularName, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = new java.util.ArrayList(); \n"
        + "this.{0}.addAll({0});{1}";
    return MessageFormat.format(codeBlockTemplate, singularName, fluentBuilder ? "\nreturn this;" : "");
  }
}
