package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;

class SingularGuavaTableHandler extends SingularMapHandler {
  private static final String COM_GOOGLE_COMMON_COLLECT_TABLE = "com.google.common.collect.Table";

  private static final String LOMBOK_ROW_KEY = "rowKey";
  private static final String LOMBOK_COLUMN_KEY = "columnKey";
  private static final String LOMBOK_VALUE = "value";

  private final boolean sortedCollection;

  SingularGuavaTableHandler(String guavaQualifiedName, boolean sortedCollection) {
    super(guavaQualifiedName);
    this.sortedCollection = sortedCollection;
  }

  public Collection<PsiField> renderBuilderFields(@NotNull BuilderInfo info) {
    final PsiType builderFieldKeyType = getBuilderFieldType(info.getFieldType(), info.getProject());
    return Collections.singleton(
      new LombokLightFieldBuilder(info.getManager(), info.getFieldName(), builderFieldKeyType)
        .withContainingClass(info.getBuilderClass())
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(info.getVariable()));
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiFieldType, @NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiType rowKeyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 0);
    final PsiType columnKeyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 1);
    final PsiType valueType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 2);

    return PsiTypeUtil.createCollectionType(psiManager, collectionQualifiedName + ".Builder", rowKeyType, columnKeyType, valueType);
  }

  protected void addOneMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName) {
    final PsiManager psiManager = methodBuilder.getManager();
    final PsiType rowKeyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 0);
    final PsiType columnKeyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 1);
    final PsiType valueType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 2);

    methodBuilder.withParameter(LOMBOK_ROW_KEY, rowKeyType);
    methodBuilder.withParameter(LOMBOK_COLUMN_KEY, columnKeyType);
    methodBuilder.withParameter(LOMBOK_VALUE, valueType);
  }

  protected void addAllMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName) {
    final PsiManager psiManager = methodBuilder.getManager();
    final PsiType rowKeyType = PsiTypeUtil.extractAllElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 0);
    final PsiType columnKeyType = PsiTypeUtil.extractAllElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 1);
    final PsiType valueType = PsiTypeUtil.extractAllElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 2);

    final PsiType collectionType = PsiTypeUtil.createCollectionType(psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, rowKeyType, columnKeyType, valueType);

    methodBuilder.withParameter(singularName, collectionType);
  }

  protected String getClearMethodBody(String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "this.{0} = null;\n {1}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, @NotNull PsiType psiFieldType, @NotNull PsiManager psiManager, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {2}.{3}; \n" +
      "this.{0}.put(" + LOMBOK_ROW_KEY + ", " + LOMBOK_COLUMN_KEY + ", " + LOMBOK_VALUE + ");{4}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, collectionQualifiedName,
      sortedCollection ? "naturalOrder()" : "builder()", fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getAllMethodBody(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull PsiManager psiManager, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {1}.{2}; \n"
      + "this.{0}.putAll({0});{3}";

    return MessageFormat.format(codeBlockTemplate, singularName, collectionQualifiedName,
      sortedCollection ? "naturalOrder()" : "builder()", fluentBuilder ? "\nreturn this;" : "");
  }

  @Override
  public String renderBuildPrepare(@NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    final PsiManager psiManager = psiVariable.getManager();
    final PsiType psiFieldType = psiVariable.getType();

    final PsiType rowKeyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 0);
    final PsiType columnKeyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 1);
    final PsiType valueType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, COM_GOOGLE_COMMON_COLLECT_TABLE, 2);

    return MessageFormat.format(
      "{4}<{1}, {2}, {3}> {0} = " +
        "this.{0} == null ? " +
        "{4}.<{1}, {2}, {3}>of() : " +
        "this.{0}.build();\n",
      fieldName, rowKeyType.getCanonicalText(false), columnKeyType.getCanonicalText(false), valueType.getCanonicalText(false), collectionQualifiedName);
  }

}
