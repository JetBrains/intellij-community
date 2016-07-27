package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
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

class SingularGuavaMapHandler extends SingularMapHandler {
  private static final String LOMBOK_KEY = "key";
  private static final String LOMBOK_VALUE = "value";

  private final boolean sortedCollection;

  SingularGuavaMapHandler(String guavaQualifiedName, boolean sortedCollection, boolean shouldGenerateFullBodyBlock) {
    super(guavaQualifiedName, shouldGenerateFullBodyBlock);
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
  protected PsiType getBuilderFieldType(@NotNull PsiType psiFieldType, @NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiType keyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 0);
    final PsiType valueType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 1);

    return PsiTypeUtil.createCollectionType(psiManager, collectionQualifiedName + ".Builder", keyType, valueType);
  }

  protected void addOneMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName) {
    final PsiManager psiManager = methodBuilder.getManager();
    final PsiType keyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 0);
    final PsiType valueType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 1);

    methodBuilder.withParameter(LOMBOK_KEY, keyType);
    methodBuilder.withParameter(LOMBOK_VALUE, valueType);
  }

  protected String getClearMethodBody(String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "this.{0} = null;\n {1}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, @NotNull PsiType psiFieldType, @NotNull PsiManager psiManager, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {2}.{3}; \n" +
      "this.{0}.put(" + LOMBOK_KEY + ", " + LOMBOK_VALUE + ");{4}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, collectionQualifiedName,
      sortedCollection ? "naturalOrder()" : "builder()",
      fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getAllMethodBody(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull PsiManager psiManager, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = {1}.{2}; \n"
      + "this.{0}.putAll({0});{3}";

    return MessageFormat.format(codeBlockTemplate, singularName, collectionQualifiedName,
      sortedCollection ? "naturalOrder()" : "builder()",
      fluentBuilder ? "\nreturn this;" : "");
  }

  @Override
  public void appendBuildPrepare(@NotNull StringBuilder buildMethodCode, @NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    final PsiManager psiManager = psiVariable.getManager();
    final PsiType psiFieldType = psiVariable.getType();

    final PsiType keyType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 0);
    final PsiType valueType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 1);

    buildMethodCode.append(MessageFormat.format(
      "{3}<{1}, {2}> {0} = " +
        "this.{0} == null ? " +
        "{3}.<{1}, {2}>of() : " +
        "this.{0}.build();\n",
      fieldName, keyType.getCanonicalText(false), valueType.getCanonicalText(false), collectionQualifiedName));
  }
}
