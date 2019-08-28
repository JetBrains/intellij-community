package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

class SingularCollectionHandler extends AbstractSingularHandler {

  SingularCollectionHandler(String qualifiedName) {
    super(qualifiedName);
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

  protected String getClearMethodBody(@NotNull BuilderInfo info) {
    final String codeBlockFormat = "if (this.{0} != null) \n this.{0}.clear();\n" +
      "return {1};";
    return MessageFormat.format(codeBlockFormat, info.getFieldName(), info.getBuilderChainResult());
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull BuilderInfo info) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = new java.util.ArrayList<{3}>(); \n" +
      "this.{0}.add({1});\n" +
      "return {2};";
    final PsiType oneElementType = PsiTypeUtil.extractOneElementType(info.getFieldType(), info.getManager());

    return MessageFormat.format(codeBlockTemplate, info.getFieldName(), singularName, info.getBuilderChainResult(),
      oneElementType.getCanonicalText(false));
  }

  protected String getAllMethodBody(@NotNull String singularName, @NotNull BuilderInfo info) {
    final String codeBlockTemplate = "if (this.{0} == null) this.{0} = new java.util.ArrayList<{2}>(); \n"
      + "this.{0}.addAll({0});\n" +
      "return {1};";
    final PsiType oneElementType = PsiTypeUtil.extractOneElementType(info.getFieldType(), info.getManager());

    return MessageFormat.format(codeBlockTemplate, singularName, info.getBuilderChainResult(),
      oneElementType.getCanonicalText(false));
  }

  @Override
  public String renderBuildPrepare(@NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    return renderBuildCode(psiVariable, fieldName, "this");
  }

  @Override
  public String renderSuperBuilderConstruction(@NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    return renderBuildCode(psiVariable, fieldName, "b") + "this." + psiVariable.getName() + "=" + fieldName + ";\n";
  }

  @Override
  public String renderBuildCode(@NotNull PsiVariable psiVariable, @NotNull String fieldName, @NotNull String builderVariable) {
    final PsiManager psiManager = psiVariable.getManager();
    final PsiType elementType = PsiTypeUtil.extractOneElementType(psiVariable.getType(), psiManager);
    String result;
    if (SingularCollectionClassNames.JAVA_UTIL_NAVIGABLE_SET.equals(collectionQualifiedName)) {
      result = "{2}<{1}> {0} = new java.util.TreeSet<{1}>();\n" +
        "if ({3}.{0} != null) {0}.addAll({3}.{0});\n" +
        "{0} = java.util.Collections.unmodifiableNavigableSet({0});\n";
    } else if (SingularCollectionClassNames.JAVA_UTIL_SORTED_SET.equals(collectionQualifiedName)) {
      result = "{2}<{1}> {0} = new java.util.TreeSet<{1}>();\n" +
        "if ({3}.{0} != null) {0}.addAll({3}.{0});\n" +
        "{0} = java.util.Collections.unmodifiableSortedSet({0});\n";
    } else if (SingularCollectionClassNames.JAVA_UTIL_SET.equals(collectionQualifiedName)) {
      result = "{2}<{1}> {0};\n" +
        "switch ({3}.{0} == null ? 0 : {3}.{0}.size()) '{'\n" +
        " case 0: \n" +
        "   {0} = java.util.Collections.emptySet();\n" +
        "   break;\n" +
        " case 1: \n" +
        "   {0} = java.util.Collections.singleton({3}.{0}.get(0));\n" +
        "   break;\n" +
        " default: \n" +
        "   {0} = new java.util.LinkedHashSet<{1}>({3}.{0}.size() < 1073741824 ? 1 + {3}.{0}.size() + ({3}.{0}.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);\n" +
        "   {0}.addAll({3}.{0});\n" +
        "   {0} = java.util.Collections.unmodifiableSet({0});\n" +
        "'}'\n";
    } else {
      result = "{2}<{1}> {0};\n" +
        "switch ({3}.{0} == null ? 0 : {3}.{0}.size()) '{'\n" +
        "case 0: \n" +
        " {0} = java.util.Collections.emptyList();\n" +
        " break;\n" +
        "case 1: \n" +
        " {0} = java.util.Collections.singletonList({3}.{0}.get(0));\n" +
        " break;\n" +
        "default: \n" +
        " {0} = java.util.Collections.unmodifiableList(new java.util.ArrayList<{1}>({3}.{0}));\n" +
        "'}'\n";
    }
    return MessageFormat.format(result, fieldName, elementType.getCanonicalText(false), collectionQualifiedName, builderVariable);
  }

  protected String getEmptyCollectionCall() {
    if (SingularCollectionClassNames.JAVA_UTIL_NAVIGABLE_SET.equals(collectionQualifiedName) ||
      SingularCollectionClassNames.JAVA_UTIL_SORTED_SET.equals(collectionQualifiedName) ||
      SingularCollectionClassNames.JAVA_UTIL_SET.equals(collectionQualifiedName)) {
      return "java.util.Collections.emptySet()";
    } else {
      return "java.util.Collections.emptyList()";
    }
  }
}
