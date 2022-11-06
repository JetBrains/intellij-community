package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.processor.handler.BuilderInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.CapitalizationStrategy;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import lombok.core.handlers.Singulars;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractSingularHandler implements BuilderElementHandler {

  private static final String BUILDER_TEMP_VAR = "builder";

  final String collectionQualifiedName;

  AbstractSingularHandler(String qualifiedName) {
    this.collectionQualifiedName = qualifiedName;
  }

  @Override
  public Collection<PsiField> renderBuilderFields(@NotNull BuilderInfo info) {
    final PsiType builderFieldType = getBuilderFieldType(info.getFieldType(), info.getProject());
    return Collections.singleton(
      new LombokLightFieldBuilder(info.getManager(), info.getFieldName(), builderFieldType)
        .withContainingClass(info.getBuilderClass())
        .withModifier(PsiModifier.PRIVATE)
        .withNavigationElement(info.getVariable()));
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiFieldType, @NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiType elementType = PsiTypeUtil.extractOneElementType(psiFieldType, psiManager);

    return PsiTypeUtil.createCollectionType(psiManager, CommonClassNames.JAVA_UTIL_ARRAY_LIST, elementType);
  }

  @Override
  public Collection<PsiMethod> renderBuilderMethod(@NotNull BuilderInfo info) {
    List<PsiMethod> methods = new ArrayList<>();

    final PsiType returnType = info.getBuilderType();
    final String fieldName = info.getFieldName();
    final String singularName = createSingularName(info.getSingularAnnotation(), fieldName);

    final PsiClass builderClass = info.getBuilderClass();
    final LombokLightMethodBuilder oneAddMethodBuilder = new LombokLightMethodBuilder(
      info.getManager(), LombokUtils.buildAccessorName(info.getSetterPrefix(), singularName, info.getCapitalizationStrategy()))
      .withContainingClass(builderClass)
      .withMethodReturnType(returnType)
      .withNavigationElement(info.getVariable())
      .withModifier(info.getVisibilityModifier())
      .withAnnotations(info.getAnnotations());

    addOneMethodParameter(oneAddMethodBuilder, info.getFieldType(), singularName);
    if(info.getVariable() instanceof PsiField psiField) {
      LombokCopyableAnnotations.copyCopyableAnnotations(psiField, oneAddMethodBuilder.getModifierList(), LombokCopyableAnnotations.COPY_TO_BUILDER_SINGULAR_SETTER);
    }

    final String oneMethodBody = getOneMethodBody(singularName, info);
    oneAddMethodBuilder.withBodyText(oneMethodBody);

    methods.add(oneAddMethodBuilder);

    final LombokLightMethodBuilder allAddMethodBuilder = new LombokLightMethodBuilder(
      info.getManager(), LombokUtils.buildAccessorName(info.getSetterPrefix(), fieldName, info.getCapitalizationStrategy()))
      .withContainingClass(builderClass)
      .withMethodReturnType(returnType)
      .withNavigationElement(info.getVariable())
      .withModifier(info.getVisibilityModifier())
      .withAnnotations(info.getAnnotations());

    addAllMethodParameter(allAddMethodBuilder, info.getFieldType(), fieldName);
    if(info.getVariable() instanceof PsiField psiField) {
      LombokCopyableAnnotations.copyCopyableAnnotations(psiField, allAddMethodBuilder.getModifierList(), LombokCopyableAnnotations.COPY_TO_SETTER);
    }

    final String allMethodBody = getAllMethodBody(fieldName, info);
    allAddMethodBuilder.withBodyText(allMethodBody);

    methods.add(allAddMethodBuilder);

    final LombokLightMethodBuilder clearMethodBuilder = new LombokLightMethodBuilder(
      info.getManager(), createSingularClearMethodName(fieldName, info.getCapitalizationStrategy()))
      .withContainingClass(builderClass)
      .withMethodReturnType(returnType)
      .withNavigationElement(info.getVariable())
      .withModifier(info.getVisibilityModifier())
      .withAnnotations(info.getAnnotations());
    final String clearMethodBlockText = getClearMethodBody(info);
    clearMethodBuilder.withBodyText(clearMethodBlockText);

    methods.add(clearMethodBuilder);

    return methods;
  }

  @NotNull
  private static String createSingularClearMethodName(String fieldName, CapitalizationStrategy capitalizationStrategy) {
    return LombokUtils.buildAccessorName("clear", fieldName, capitalizationStrategy);
  }

  @Override
  public List<String> getBuilderMethodNames(@NotNull String fieldName, @Nullable PsiAnnotation singularAnnotation,
                                            CapitalizationStrategy capitalizationStrategy) {
    return Arrays.asList(createSingularName(singularAnnotation, fieldName),
                         fieldName,
                         createSingularClearMethodName(fieldName, capitalizationStrategy));
  }

  @Override
  public String renderToBuilderCall(@NotNull BuilderInfo info) {
    final String instanceGetter = info.getInstanceVariableName() + '.' + info.getVariable().getName();
    return info.getFieldName() + '(' + instanceGetter + " == null ? " + getEmptyCollectionCall(info) + " : " + instanceGetter + ')';
  }

  @Override
  public String renderToBuilderAppendCall(@NotNull BuilderInfo info) {
    final String instanceGetter = info.getInstanceVariableName() + '.' + info.getVariable().getName();
    return "if(" + instanceGetter + " != null) "+BUILDER_TEMP_VAR +"."+info.getFieldName() +'('+ instanceGetter + ");";
  }

  protected abstract String getEmptyCollectionCall(@NotNull BuilderInfo info);

  protected abstract String getClearMethodBody(@NotNull BuilderInfo info);

  protected abstract void addOneMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName);

  protected abstract void addAllMethodParameter(@NotNull LombokLightMethodBuilder methodBuilder, @NotNull PsiType psiFieldType, @NotNull String singularName);

  protected abstract String getOneMethodBody(@NotNull String singularName, @NotNull BuilderInfo info);

  protected abstract String getAllMethodBody(@NotNull String singularName, @NotNull BuilderInfo info);

  @Override
  public String createSingularName(@NotNull PsiAnnotation singularAnnotation, String psiFieldName) {
    String singularName = PsiAnnotationUtil.getStringAnnotationValue(singularAnnotation, "value", "");
    if (StringUtil.isEmptyOrSpaces(singularName)) {
      singularName = Singulars.autoSingularize(psiFieldName);
      if (singularName == null) {
        singularName = psiFieldName;
      }
    }
    return singularName;
  }

  public static boolean validateSingularName(PsiAnnotation singularAnnotation, String psiFieldName) {
    String singularName = PsiAnnotationUtil.getStringAnnotationValue(singularAnnotation, "value", "");
    if (StringUtil.isEmptyOrSpaces(singularName)) {
      singularName = Singulars.autoSingularize(psiFieldName);
      return singularName != null;
    }
    return true;
  }

}
