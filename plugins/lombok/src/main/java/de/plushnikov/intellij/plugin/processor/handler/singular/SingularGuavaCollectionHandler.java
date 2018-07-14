package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

class SingularGuavaCollectionHandler extends SingularCollectionHandler {

  private final boolean sortedCollection;
  private final String typeCollectionQualifiedName;

  SingularGuavaCollectionHandler(String collectionQualifiedName, boolean sortedCollection) {
    super(collectionQualifiedName);
    this.sortedCollection = sortedCollection;
    this.typeCollectionQualifiedName = SingularCollectionClassNames.GUAVA_IMMUTABLE_COLLECTION.equals(collectionQualifiedName)
      ? SingularCollectionClassNames.GUAVA_IMMUTABLE_LIST : collectionQualifiedName;
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiFieldType, @NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiType elementType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager);

    return PsiTypeUtil.createCollectionType(psiManager, typeCollectionQualifiedName + ".Builder", elementType);
  }

  protected void addAllMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName) {
    final PsiManager psiManager = methodBuilder.getManager();
    final PsiType elementType = PsiTypeUtil.extractAllElementType(psiFieldType, psiManager);
    final PsiType collectionType = PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_LANG_ITERABLE, elementType);

    methodBuilder.withParameter(singularName, collectionType);
  }

  protected String getClearMethodBody(String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "this.{0} = null;\n {1}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, @NotNull PsiType psiFieldType, @NotNull PsiManager psiManager, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {2}.{3}; \n" +
      "this.{0}.add({1});{4}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, typeCollectionQualifiedName,
      sortedCollection ? "naturalOrder()" : "builder()", fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getAllMethodBody(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull PsiManager psiManager, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {1}.{2}; \n"
      + "this.{0}.addAll({0});{3}";

    return MessageFormat.format(codeBlockTemplate, singularName, typeCollectionQualifiedName,
      sortedCollection ? "naturalOrder()" : "builder()", fluentBuilder ? "\nreturn this;" : "");
  }

  @Override
  public String renderBuildPrepare(@NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    final PsiManager psiManager = psiVariable.getManager();
    final PsiType psiFieldType = psiVariable.getType();

    final PsiType elementType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager);
    return MessageFormat.format(
      "{2}<{1}> {0} = " +
        "this.{0} == null ? " +
        "{3}.<{1}>of() : " +
        "this.{0}.build();\n",
      fieldName, elementType.getCanonicalText(false), collectionQualifiedName, typeCollectionQualifiedName);
  }
}
