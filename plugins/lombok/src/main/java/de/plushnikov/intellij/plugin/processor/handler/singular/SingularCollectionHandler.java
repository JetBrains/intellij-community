package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

class SingularCollectionHandler extends AbstractSingularHandler {

  SingularCollectionHandler(String qualifiedName) {
    super(qualifiedName);
  }

  @NotNull
  private static PsiType getOneElementType(@NotNull PsiType psiFieldType, PsiManager psiManager) {
    return PsiTypeUtil.extractOneElementType(psiFieldType, psiManager);
  }

  @NotNull
  private static PsiType getCollectionType(@NotNull PsiType psiFieldType, @NotNull PsiManager psiManager) {
    final PsiType elementType = PsiTypeUtil.extractAllElementType(psiFieldType, psiManager);
    return PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_UTIL_COLLECTION, elementType);
  }

  @Override
  protected List<PsiType> getOneMethodParameterTypes(@NotNull BuilderInfo info) {
    final PsiType oneElementType = getOneElementType(info.getFieldType(), info.getManager());
    return List.of(oneElementType);
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
    final PsiType oneElementType = getOneElementType(psiFieldType, methodBuilder.getManager());
    methodBuilder.withParameter(singularName, oneElementType);
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
    final String codeBlockFormat = """
      if (this.{0} != null)\s
       this.{0}.clear();
      return {1};""";
    return MessageFormat.format(codeBlockFormat, info.getFieldName(), info.getBuilderChainResult());
  }

  @Override
  protected String getOneMethodBody(@NotNull String singularName, @NotNull BuilderInfo info) {
    final String codeBlockTemplate = """
      if (this.{0} == null) this.{0} = new java.util.ArrayList<{3}>();\s
      this.{0}.add({1});
      return {2};""";
    final PsiType oneElementType = getOneElementType(info.getFieldType(), info.getManager());

    return MessageFormat.format(codeBlockTemplate, info.getFieldName(), singularName, info.getBuilderChainResult(),
                                oneElementType.getCanonicalText(false));
  }

  @Override
  protected String getAllMethodBody(@NotNull String singularName, @NotNull BuilderInfo info) {
    final String codeBlockTemplate = """
      if({0}==null)'{'throw new NullPointerException("{0} cannot be null");'}'
      if (this.{0} == null) this.{0} = new java.util.ArrayList<{2}>();\s
      this.{0}.addAll({0});
      return {1};""";
    final PsiType oneElementType = getOneElementType(info.getFieldType(), info.getManager());

    return MessageFormat.format(codeBlockTemplate, singularName, info.getBuilderChainResult(),
                                oneElementType.getCanonicalText(false));
  }

  @Override
  public String renderBuildPrepare(@NotNull BuilderInfo info) {
    return renderBuildCode(info.getVariable(), info.getFieldName(), "this");
  }

  @Override
  public String renderBuildCall(@NotNull BuilderInfo info) {
    return info.renderFieldName();
  }

  @Override
  public String renderSuperBuilderConstruction(@NotNull BuilderInfo info) {
    final PsiVariable psiVariable = info.getVariable();
    final String fieldName = info.renderFieldName();
    return renderBuildCode(psiVariable, fieldName, "b") + "this." + psiVariable.getName() + "=" + fieldName + ";\n";
  }

  String renderBuildCode(@NotNull PsiVariable psiVariable, @NotNull String fieldName, @NotNull String builderVariable) {
    final PsiManager psiManager = psiVariable.getManager();
    final PsiType elementType = getOneElementType(psiVariable.getType(), psiManager);
    String result;
    if (SingularCollectionClassNames.JAVA_UTIL_NAVIGABLE_SET.equals(collectionQualifiedName)) {
      result = """
        {2}<{1}> {0} = new java.util.TreeSet<{1}>();
        if ({3}.{0} != null) {0}.addAll({3}.{0});
        {0} = java.util.Collections.unmodifiableNavigableSet({0});
        """;
    }
    else if (SingularCollectionClassNames.JAVA_UTIL_SORTED_SET.equals(collectionQualifiedName)) {
      result = """
        {2}<{1}> {0} = new java.util.TreeSet<{1}>();
        if ({3}.{0} != null) {0}.addAll({3}.{0});
        {0} = java.util.Collections.unmodifiableSortedSet({0});
        """;
    }
    else if (SingularCollectionClassNames.JAVA_UTIL_SET.equals(collectionQualifiedName)) {
      result = """
        {2}<{1}> {0};
        switch ({3}.{0} == null ? 0 : {3}.{0}.size()) '{'
         case 0:\s
           {0} = java.util.Collections.emptySet();
           break;
         case 1:\s
           {0} = java.util.Collections.singleton({3}.{0}.get(0));
           break;
         default:\s
           {0} = new java.util.LinkedHashSet<{1}>({3}.{0}.size() < 1073741824 ? 1 + {3}.{0}.size() + ({3}.{0}.size() - 3) / 3 : java.lang.Integer.MAX_VALUE);
           {0}.addAll({3}.{0});
           {0} = java.util.Collections.unmodifiableSet({0});
        '}'
        """;
    }
    else {
      result = """
        {2}<{1}> {0};
        switch ({3}.{0} == null ? 0 : {3}.{0}.size()) '{'
        case 0:\s
         {0} = java.util.Collections.emptyList();
         break;
        case 1:\s
         {0} = java.util.Collections.singletonList({3}.{0}.get(0));
         break;
        default:\s
         {0} = java.util.Collections.unmodifiableList(new java.util.ArrayList<{1}>({3}.{0}));
        '}'
        """;
    }
    return MessageFormat.format(result, fieldName, elementType.getCanonicalText(false), collectionQualifiedName, builderVariable);
  }

  @Override
  protected String getEmptyCollectionCall(@NotNull BuilderInfo info) {
    final PsiType elementType = getOneElementType(info.getVariable().getType(), info.getManager());
    final String typeName = elementType.getCanonicalText(false);
    if (ContainerUtil.exists(SingularCollectionClassNames.JAVA_SETS, collectionQualifiedName::equals)) {
      return "java.util.Collections.<" + typeName + ">emptySet()";
    }
    else {
      return "java.util.Collections.<" + typeName + ">emptyList()";
    }
  }
}
