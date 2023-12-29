package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;

class SingularMapHandler extends AbstractSingularHandler {

  private static final String KEY = "Key";
  private static final String VALUE = "Value";
  private static final String LOMBOK_KEY = "$key";
  private static final String LOMBOK_VALUE = "$value";

  SingularMapHandler(String qualifiedName) {
    super(qualifiedName);
  }

  @NotNull
  protected static PsiType getKeyType(PsiType psiFieldType, PsiManager psiManager) {
    return PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 0);
  }

  @NotNull
  protected PsiType getValueType(PsiType psiFieldType, PsiManager psiManager) {
    return PsiTypeUtil.extractOneElementType(psiFieldType, psiManager, CommonClassNames.JAVA_UTIL_MAP, 1);
  }

  @Override
  public Collection<PsiField> renderBuilderFields(@NotNull BuilderInfo info) {
    final PsiType keyType = getKeyType(info.getFieldType(), info.getManager());
    final PsiType builderFieldKeyType = getBuilderFieldType(keyType, info.getProject());

    final PsiType valueType = getValueType(info.getFieldType(), info.getManager());
    final PsiType builderFieldValueType = getBuilderFieldType(valueType, info.getProject());

    return List.of(
      new LombokLightFieldBuilder(info.getManager(), info.getFieldName() + LOMBOK_KEY, builderFieldKeyType)
        .withContainingClass(info.getBuilderClass())
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(info.getVariable()),
      new LombokLightFieldBuilder(info.getManager(), info.getFieldName() + LOMBOK_VALUE, builderFieldValueType)
        .withContainingClass(info.getBuilderClass())
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(info.getVariable()));
  }

  @Override
  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiType, @NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    return PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_UTIL_ARRAY_LIST, psiType);
  }

  @NotNull
  private static PsiType getCollectionType(PsiType info, PsiManager psiManager) {
    final PsiType keyType = PsiTypeUtil.extractAllElementType(info, psiManager, CommonClassNames.JAVA_UTIL_MAP, 0);
    final PsiType valueType = PsiTypeUtil.extractAllElementType(info, psiManager, CommonClassNames.JAVA_UTIL_MAP, 1);
    return PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_UTIL_MAP, keyType, valueType);
  }

  @Override
  protected List<PsiType> getOneMethodParameterTypes(@NotNull BuilderInfo info) {
    return List.of(getKeyType(info.getFieldType(), info.getManager()), getValueType(info.getFieldType(), info.getManager()));
  }

  @Override
  protected List<PsiType> getAllMethodParameterTypes(@NotNull BuilderInfo info) {
    final PsiType collectionType = getCollectionType(info.getFieldType(), info.getManager());
    return List.of(collectionType);
  }

  @Override
  protected void addOneMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder,
                                       @NotNull PsiType psiFieldType,
                                       @NotNull String singularName) {
    final PsiManager psiManager = methodBuilder.getManager();
    final PsiType keyType = getKeyType(psiFieldType, psiManager);
    final PsiType valueType = getValueType(psiFieldType, psiManager);

    methodBuilder.withParameter(singularName + KEY, keyType);
    methodBuilder.withParameter(singularName + VALUE, valueType);
  }

  @Override
  protected void addAllMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder,
                                       @NotNull PsiType psiFieldType,
                                       @NotNull String singularName) {
    final PsiType collectionType = getCollectionType(psiFieldType, methodBuilder.getManager());

    methodBuilder.withParameter(singularName, collectionType);
  }

  @Override
  protected String getClearMethodBody(@NotNull BuilderInfo info) {
    final String codeBlockFormat = "if (this.{0}" + LOMBOK_KEY + " != null) '{'\n this.{0}" + LOMBOK_KEY + ".clear();\n " +
                                   " this.{0}" + LOMBOK_VALUE + ".clear(); '}'\n" +
                                   "return {1};";
    return MessageFormat.format(codeBlockFormat, info.getFieldName(), info.getBuilderChainResult());
  }

  @Override
  protected String getOneMethodBody(@NotNull String singularName, @NotNull BuilderInfo info) {
    final String codeBlockTemplate = "if (this.{0}" + LOMBOK_KEY + " == null) '{' \n" +
                                     "this.{0}" + LOMBOK_KEY + " = new java.util.ArrayList<{3}>(); \n" +
                                     "this.{0}" + LOMBOK_VALUE + " = new java.util.ArrayList<{4}>(); \n" +
                                     "'}' \n" +
                                     "this.{0}" + LOMBOK_KEY + ".add({1}" + KEY + ");\n" +
                                     "this.{0}" + LOMBOK_VALUE + ".add({1}" + VALUE + ");\n" +
                                     "return {2};";

    final PsiType keyType = getKeyType(info.getFieldType(), info.getManager());
    final PsiType valueType = getValueType(info.getFieldType(), info.getManager());

    return MessageFormat.format(codeBlockTemplate, info.getFieldName(), singularName, info.getBuilderChainResult(),
                                keyType.getCanonicalText(false), valueType.getCanonicalText(false));
  }

  @Override
  protected String getAllMethodBody(@NotNull String singularName, @NotNull BuilderInfo info) {
    final String codeBlockTemplate = "if({0}==null)'{'throw new NullPointerException(\"{0} cannot be null\");'}'\n" +
                                     "if (this.{0}" + LOMBOK_KEY + " == null) '{' \n" +
                                     "this.{0}" + LOMBOK_KEY + " = new java.util.ArrayList<{2}>(); \n" +
                                     "this.{0}" + LOMBOK_VALUE + " = new java.util.ArrayList<{3}>(); \n" +
                                     "'}' \n" +
                                     "for (final java.util.Map.Entry<{4},{5}> $lombokEntry : {0}.entrySet()) '{'\n" +
                                     "this.{0}" + LOMBOK_KEY + ".add($lombokEntry.getKey());\n" +
                                     "this.{0}" + LOMBOK_VALUE + ".add($lombokEntry.getValue());\n" +
                                     "'}'\n" +
                                     "return {1};";

    final PsiType keyType = getKeyType(info.getFieldType(), info.getManager());
    final PsiType valueType = getValueType(info.getFieldType(), info.getManager());

    final PsiType keyIterType =
      PsiTypeUtil.extractAllElementType(info.getFieldType(), info.getManager(), CommonClassNames.JAVA_UTIL_MAP, 0);
    final PsiType valueIterType =
      PsiTypeUtil.extractAllElementType(info.getFieldType(), info.getManager(), CommonClassNames.JAVA_UTIL_MAP, 1);

    return MessageFormat.format(codeBlockTemplate, singularName, info.getBuilderChainResult(),
                                keyType.getCanonicalText(false), valueType.getCanonicalText(false),
                                keyIterType.getCanonicalText(false), valueIterType.getCanonicalText(false));
  }

  @Override
  public String renderBuildPrepare(@NotNull BuilderInfo info) {
    return renderBuildCode(info.getVariable(), info.getFieldName(), "this");
  }

  @Override
  public String renderBuildCall(@NotNull BuilderInfo info) {
    return info.renderFieldName();
  }

  String renderBuildCode(@NotNull PsiVariable psiVariable, @NotNull String fieldName, @NotNull String builderVariable) {
    final PsiManager psiManager = psiVariable.getManager();
    final PsiType psiFieldType = psiVariable.getType();
    final PsiType keyType = getKeyType(psiFieldType, psiManager);
    final PsiType valueType = getValueType(psiFieldType, psiManager);

    final String selectedFormat;
    if (collectionQualifiedName.equals(SingularCollectionClassNames.JAVA_UTIL_SORTED_MAP)) {
      selectedFormat = """
        java.util.SortedMap<{1}, {2}> {0} = new java.util.TreeMap<{1}, {2}>();
              if ({3}.{0}$key != null) for (int $i = 0; $i < ({3}.{0}$key == null ? 0 : {3}.{0}$key.size()); $i++) {0}.put({3}.{0}$key.get($i), ({2}){3}.{0}$value.get($i));
              {0} = java.util.Collections.unmodifiableSortedMap({0});
        """;
    }
    else if (collectionQualifiedName.equals(SingularCollectionClassNames.JAVA_UTIL_NAVIGABLE_MAP)) {
      selectedFormat = """
        java.util.NavigableMap<{1}, {2}> {0} = new java.util.TreeMap<{1}, {2}>();
              if ({3}.{0}$key != null) for (int $i = 0; $i < ({3}.{0}$key == null ? 0 : {3}.{0}$key.size()); $i++) {0}.put({3}.{0}$key.get($i), ({2}){3}.{0}$value.get($i));
              {0} = java.util.Collections.unmodifiableNavigableMap({0});
        """;
    }
    else {
      selectedFormat = """
        java.util.Map<{1}, {2}> {0};
          switch ({3}.{0}$key == null ? 0 : {3}.{0}$key.size()) '{'
            case 0:
              {0} = java.util.Collections.emptyMap();
              break;
            case 1:
              {0} = java.util.Collections.singletonMap({3}.{0}$key.get(0), {3}.{0}$value.get(0));
              break;
            default:
              {0} = new java.util.LinkedHashMap<{1}, {2}>({3}.{0}$key.size() < 1073741824 ? 1 + {3}.{0}$key.size() + ({3}.{0}$key.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
              for (int $i = 0; $i < {3}.{0}$key.size(); $i++) {0}.put({3}.{0}$key.get($i), ({2}){3}.{0}$value.get($i));
              {0} = java.util.Collections.unmodifiableMap({0});
          '}'
        """;
    }
    return MessageFormat.format(selectedFormat, fieldName, keyType.getCanonicalText(false),
                                valueType.getCanonicalText(false), builderVariable);
  }

  @Override
  public String renderSuperBuilderConstruction(@NotNull BuilderInfo info) {
    final PsiVariable psiVariable = info.getVariable();
    final String fieldName = info.renderFieldName();
    final String basicCode = renderBuildCode(psiVariable, fieldName, "b");
    final String assignment = "this." + psiVariable.getName() + "=" + fieldName + ";\n";
    return basicCode + assignment;
  }

  @Override
  protected String getEmptyCollectionCall(@NotNull BuilderInfo info) {
    final PsiManager psiManager = info.getManager();
    final PsiType psiFieldType = info.getVariable().getType();
    final PsiType keyType = getKeyType(psiFieldType, psiManager);
    final PsiType valueType = getValueType(psiFieldType, psiManager);
    final String keyTypeName = keyType.getCanonicalText(false);
    final String valueTypeName = valueType.getCanonicalText(false);

    return "java.util.Collections.<" + keyTypeName + "," + valueTypeName + ">emptyMap()";
  }
}
