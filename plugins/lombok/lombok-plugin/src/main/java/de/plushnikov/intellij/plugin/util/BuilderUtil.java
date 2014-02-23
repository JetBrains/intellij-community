package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuilderUtil {
  public final static String ANNOTATION_BUILDER_CLASS_NAME = "builderClassName";
  public static final String ANNOTATION_BUILD_METHOD_NAME = "buildMethodName";
  public static final String ANNOTATION_BUILDER_METHOD_NAME = "builderMethodName";
  public static final String ANNOTATION_FLUENT = "fluent";
  public static final String ANNOTATION_CHAIN = "chain";

  public final static String BUILDER_CLASS_NAME = "Builder";
  public final static String BUILD_METHOD_NAME = "build";
  public final static String BUILDER_METHOD_NAME = "builder";
  public final static String SETTER_PREFIX = "set";

  public static String createBuilderClassName(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass) {
    return createBuilderClassNameWithGenerics(psiAnnotation, psiClass.getName());
  }

  public static String createBuilderClassNameWithGenerics(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass) {
    final PsiType psiType = PsiClassUtil.getTypeWithGenerics(psiClass);
    return createBuilderClassNameWithGenerics(psiAnnotation, psiType.getPresentableText());
  }

  public static String createBuilderClassNameWithGenerics(@NotNull PsiAnnotation psiAnnotation, @Nullable PsiType psiType) {
    return createBuilderClassNameWithGenerics(psiAnnotation, psiType != null ? psiType.getPresentableText() : PsiType.VOID.getBoxedTypeName());
  }

  public static String createBuilderClassNameWithGenerics(@NotNull PsiAnnotation psiAnnotation, @NotNull String type) {
    final String builderClassName = getBuilderClassName(psiAnnotation);
    if (StringUtils.isNotBlank(builderClassName)) {
      return builderClassName;
    }
    // Add suffix before generics declaration
    int indexForSuffix = type.indexOf("<");
    if (indexForSuffix > -1) {
      return StringUtils.capitalize(type.substring(0, indexForSuffix) + BUILDER_CLASS_NAME + type.substring(indexForSuffix, type.length()));
    }
    return StringUtils.capitalize(type) + BUILDER_CLASS_NAME;
  }

  public static String getBuilderClassName(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_CLASS_NAME, String.class);
  }

  @NotNull
  public static String createSetterName(@NotNull PsiAnnotation psiAnnotation, @NotNull String fieldName) {
    Boolean fluentAnnotationValue = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_FLUENT, Boolean.class);
    final boolean isFluent = fluentAnnotationValue != null ? fluentAnnotationValue : true;
    return isFluent ? fieldName : SETTER_PREFIX + StringUtils.capitalize(fieldName);
  }

  @NotNull
  public static PsiType createSetterReturnType(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiType fieldType) {
    Boolean chainAnnotationValue = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_CHAIN, Boolean.class);
    final boolean isChain = chainAnnotationValue != null ? chainAnnotationValue : true;
    return isChain ? fieldType : PsiType.VOID;
  }

  @NotNull
  public static String createBuildMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String buildMethodName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_BUILD_METHOD_NAME, String.class);
    return StringUtils.isNotBlank(buildMethodName) ? buildMethodName : BUILD_METHOD_NAME;
  }

  @NotNull
  public static String createBuilderMethodName(@NotNull PsiAnnotation psiAnnotation) {
    final String builderMethodName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, ANNOTATION_BUILDER_METHOD_NAME, String.class);
    return StringUtils.isNotBlank(builderMethodName) ? builderMethodName : BUILDER_METHOD_NAME;
  }
}
