package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
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

  @Override
  public String renderBuildPrepare(@NotNull PsiVariable psiVariable, @NotNull String fieldName) {
    final PsiManager psiManager = psiVariable.getManager();
    final PsiType elementType = PsiTypeUtil.extractOneElementType(psiVariable.getType(), psiManager);

    final String selectedFormat;
    if (SingularCollectionClassNames.JAVA_UTIL_NAVIGABLE_SET.equals(collectionQualifiedName)) {
      selectedFormat = "{2}<{1}> {0} = new java.util.TreeSet<{1}>();\n" +
        "if (this.{0} != null) {0}.addAll(this.{0});\n" +
        "{0} = java.util.Collections.unmodifiableNavigableSet({0});\n";
    } else if (SingularCollectionClassNames.JAVA_UTIL_SORTED_SET.equals(collectionQualifiedName)) {
      selectedFormat = "{2}<{1}> {0} = new java.util.TreeSet<{1}>();\n" +
        "if (this.{0} != null) {0}.addAll(this.{0});\n" +
        "{0} = java.util.Collections.unmodifiableSortedSet({0});\n";
    } else if (SingularCollectionClassNames.JAVA_UTIL_SET.equals(collectionQualifiedName)) {
      selectedFormat = "{2}<{1}> {0};\n" +
        "switch (this.{0} == null ? 0 : this.{0}.size()) '{'\n" +
        " case 0: \n" +
        "   {0} = java.util.Collections.emptySet();\n" +
        "   break;\n" +
        " case 1: \n" +
        "   {0} = java.util.Collections.singleton(this.{0}.get(0));\n" +
        "   break;\n" +
        " default: \n" +
        "   {0} = new java.util.LinkedHashSet<{1}>(this.{0}.size() < 1073741824 ? 1 + this.{0}.size() + (this.{0}.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);\n" +
        "   {0}.addAll(this.{0});\n" +
        "   {0} = java.util.Collections.unmodifiableSet({0});\n" +
        "'}'\n";
    } else {
      selectedFormat = "{2}<{1}> {0};\n" +
        "switch (this.{0} == null ? 0 : this.{0}.size()) '{'\n" +
        "case 0: \n" +
        " {0} = java.util.Collections.emptyList();\n" +
        " break;\n" +
        "case 1: \n" +
        " {0} = java.util.Collections.singletonList(this.{0}.get(0));\n" +
        " break;\n" +
        "default: \n" +
        " {0} = java.util.Collections.unmodifiableList(new java.util.ArrayList<{1}>(this.{0}));\n" +
        "'}'\n";
    }

    return MessageFormat.format(selectedFormat, fieldName, elementType.getCanonicalText(false), collectionQualifiedName);
  }
}
