package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

class SingularCollectionHandler extends AbstractSingularHandler {

  SingularCollectionHandler(boolean shouldGenerateFullBodyBlock) {
    super(shouldGenerateFullBodyBlock);
  }

  protected void addOneMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName) {
    final PsiType oneElementType = PsiTypeUtil.extractOneElementType(psiFieldType, methodBuilder.getManager());
    methodBuilder.withParameter(singularName, oneElementType);
  }

  protected void addAllMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName) {
    final PsiManager psiManager = methodBuilder.getManager();
    final PsiType elementType = PsiTypeUtil.extractAllElementType(psiFieldType, psiManager);
    final PsiType collectionType = PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_UTIL_COLLECTION, elementType);
    methodBuilder.withParameter(singularName, collectionType);
  }

  protected String getClearMethodBody(String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} != null) \n this.{0}.clear();\n {1}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, @NotNull PsiType psiFieldType, @NotNull PsiManager psiManager, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = new java.util.ArrayList<{3}>(); \n" +
        "this.{0}.add({1});{2}";
    final PsiType oneElementType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager);

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, fluentBuilder ? "\nreturn this;" : "",
        oneElementType.getCanonicalText(false));
  }

  protected String getAllMethodBody(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull PsiManager psiManager, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = new java.util.ArrayList<{2}>(); \n"
        + "this.{0}.addAll({0});{1}";
    final PsiType oneElementType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager);

    return MessageFormat.format(codeBlockTemplate, singularName, fluentBuilder ? "\nreturn this;" : "",
        oneElementType.getCanonicalText(false));
  }
}
