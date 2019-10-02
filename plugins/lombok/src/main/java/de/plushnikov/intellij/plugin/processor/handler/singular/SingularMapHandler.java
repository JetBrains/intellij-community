package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
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
import java.util.Arrays;
import java.util.Collection;

class SingularMapHandler extends AbstractSingularHandler {

  private static final String KEY = "Key";
  private static final String VALUE = "Value";
  private static final String LOMBOK_KEY = "$key";
  private static final String LOMBOK_VALUE = "$value";

  SingularMapHandler(String qualifiedName) {
    super(qualifiedName);
  }

  @NotNull
  private PsiType getKeyType(PsiManager psiManager, PsiType psiFieldType) {
    return PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 0);
  }

  @NotNull
  private PsiType getValueType(PsiManager psiManager, PsiType psiFieldType) {
    return PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 1);
  }

  public Collection<PsiField> renderBuilderFields(@NotNull BuilderInfo info) {
    final PsiType keyType = getKeyType(info.getManager(), info.getFieldType());
    final PsiType builderFieldKeyType = getBuilderFieldType(keyType, info.getProject());

    final PsiType valueType = getValueType(info.getManager(), info.getFieldType());
    final PsiType builderFieldValueType = getBuilderFieldType(valueType, info.getProject());

    return Arrays.asList(
      new LombokLightFieldBuilder(info.getManager(), info.getFieldName() + LOMBOK_KEY, builderFieldKeyType)
        .withContainingClass(info.getBuilderClass())
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(info.getVariable()),
      new LombokLightFieldBuilder(info.getManager(), info.getFieldName() + LOMBOK_VALUE, builderFieldValueType)
        .withContainingClass(info.getBuilderClass())
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(info.getVariable()));
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiType, @NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    return PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_UTIL_ARRAY_LIST, psiType);
  }

  protected void addOneMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName) {
    final PsiManager psiManager = methodBuilder.getManager();
    final PsiType keyType = getKeyType(psiManager, psiFieldType);
    final PsiType valueType = getValueType(psiManager, psiFieldType);

    methodBuilder.withParameter(singularName + KEY, keyType);
    methodBuilder.withParameter(singularName + VALUE, valueType);
  }

  protected void addAllMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName) {
    final PsiManager psiManager = methodBuilder.getManager();
    final PsiType keyType = PsiTypeUtil.extractAllElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 0);
    final PsiType valueType = PsiTypeUtil.extractAllElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 1);

    final PsiType collectionType = PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_UTIL_MAP, keyType, valueType);

    methodBuilder.withParameter(singularName, collectionType);
  }

  protected String getClearMethodBody(@NotNull BuilderInfo info) {
    final String codeBlockFormat = "if (this.{0}" + LOMBOK_KEY + " != null) '{'\n this.{0}" + LOMBOK_KEY + ".clear();\n " +
      " this.{0}" + LOMBOK_VALUE + ".clear(); '}'\n" +
      "return {1};";
    return MessageFormat.format(codeBlockFormat, info.getFieldName(), info.getBuilderChainResult());
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull BuilderInfo info) {
    final String codeBlockTemplate = "if (this.{0}" + LOMBOK_KEY + " == null) '{' \n" +
      "this.{0}" + LOMBOK_KEY + " = new java.util.ArrayList<{3}>(); \n" +
      "this.{0}" + LOMBOK_VALUE + " = new java.util.ArrayList<{4}>(); \n" +
      "'}' \n" +
      "this.{0}" + LOMBOK_KEY + ".add({1}" + KEY + ");\n" +
      "this.{0}" + LOMBOK_VALUE + ".add({1}" + VALUE + ");\n" +
      "return {2};";

    final PsiType keyType = getKeyType(info.getManager(), info.getFieldType());
    final PsiType valueType = getValueType(info.getManager(), info.getFieldType());

    return MessageFormat.format(codeBlockTemplate, info.getFieldName(), singularName, info.getBuilderChainResult(),
      keyType.getCanonicalText(false), valueType.getCanonicalText(false));
  }

  protected String getAllMethodBody(@NotNull String singularName, @NotNull BuilderInfo info) {
    final String codeBlockTemplate = "if (this.{0}" + LOMBOK_KEY + " == null) '{' \n" +
      "this.{0}" + LOMBOK_KEY + " = new java.util.ArrayList<{2}>(); \n" +
      "this.{0}" + LOMBOK_VALUE + " = new java.util.ArrayList<{3}>(); \n" +
      "'}' \n" +
      "for (final java.util.Map.Entry<{4},{5}> $lombokEntry : {0}.entrySet()) '{'\n" +
      "this.{0}" + LOMBOK_KEY + ".add($lombokEntry.getKey());\n" +
      "this.{0}" + LOMBOK_VALUE + ".add($lombokEntry.getValue());\n" +
      "'}'\n" +
      "return {1};";

    final PsiType keyType = getKeyType(info.getManager(), info.getFieldType());
    final PsiType valueType = getValueType(info.getManager(), info.getFieldType());

    final PsiType keyIterType = PsiTypeUtil.extractAllElementType(info.getFieldType(), info.getManager(), CommonClassNames.JAVA_UTIL_MAP, 0);
    final PsiType valueIterType = PsiTypeUtil.extractAllElementType(info.getFieldType(), info.getManager(), CommonClassNames.JAVA_UTIL_MAP, 1);

    return MessageFormat.format(codeBlockTemplate, singularName, info.getBuilderChainResult(),
      keyType.getCanonicalText(false), valueType.getCanonicalText(false),
      keyIterType.getCanonicalText(false), valueIterType.getCanonicalText(false));
  }

  @Override
  public String renderBuildPrepare(@NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    return renderBuildCode(psiVariable, fieldName, "this");
  }

  @Override
  public String renderBuildCode(@NotNull PsiVariable psiVariable, @NotNull String fieldName, @NotNull String builderVariable) {
    final PsiManager psiManager = psiVariable.getManager();
    final PsiType psiFieldType = psiVariable.getType();
    final PsiType keyType = getKeyType(psiManager, psiFieldType);
    final PsiType valueType = getValueType(psiManager, psiFieldType);

    final String selectedFormat;
    if (collectionQualifiedName.equals(SingularCollectionClassNames.JAVA_UTIL_SORTED_MAP)) {
      selectedFormat = "java.util.SortedMap<{1}, {2}> {0} = new java.util.TreeMap<{1}, {2}>();\n" +
        "      if ({3}.{0}$key != null) for (int $i = 0; $i < ({3}.{0}$key == null ? 0 : {3}.{0}$key.size()); $i++) {0}.put({3}.{0}$key.get($i), ({2}){3}.{0}$value.get($i));\n" +
        "      {0} = java.util.Collections.unmodifiableSortedMap({0});\n";
    } else if (collectionQualifiedName.equals(SingularCollectionClassNames.JAVA_UTIL_NAVIGABLE_MAP)) {
      selectedFormat = "java.util.NavigableMap<{1}, {2}> {0} = new java.util.TreeMap<{1}, {2}>();\n" +
        "      if ({3}.{0}$key != null) for (int $i = 0; $i < ({3}.{0}$key == null ? 0 : {3}.{0}$key.size()); $i++) {0}.put({3}.{0}$key.get($i), ({2}){3}.{0}$value.get($i));\n" +
        "      {0} = java.util.Collections.unmodifiableNavigableMap({0});\n";
    } else {
      selectedFormat = "java.util.Map<{1}, {2}> {0};\n" +
        "  switch ({3}.{0}$key == null ? 0 : {3}.{0}$key.size()) '{'\n" +
        "    case 0:\n" +
        "      {0} = java.util.Collections.emptyMap();\n" +
        "      break;\n" +
        "    case 1:\n" +
        "      {0} = java.util.Collections.singletonMap({3}.{0}$key.get(0), {3}.{0}$value.get(0));\n" +
        "      break;\n" +
        "    default:\n" +
        "      {0} = new java.util.LinkedHashMap<{1}, {2}>({3}.{0}$key.size() < 1073741824 ? 1 + {3}.{0}$key.size() + ({3}.{0}$key.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);\n" +
        "      for (int $i = 0; $i < {3}.{0}$key.size(); $i++) {0}.put({3}.{0}$key.get($i), ({2}){3}.{0}$value.get($i));\n" +
        "      {0} = java.util.Collections.unmodifiableMap({0});\n" +
        "  '}'\n";
    }
    return MessageFormat.format(selectedFormat, fieldName, keyType.getCanonicalText(false),
      valueType.getCanonicalText(false), builderVariable);
  }

  public String renderSuperBuilderConstruction(@NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    final String basicCode = renderBuildCode(psiVariable, fieldName, "b");
    final String assignment = "this." + psiVariable.getName() + "=" + fieldName + ";\n";
    return basicCode + assignment;
  }

  @Override
  protected String getEmptyCollectionCall() {
    return "java.util.Collections.emptyMap()";
  }
}
