package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.psi.LombokEnumConstantBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class FieldNameConstantsHandler {

  public static String getTypeName(@NotNull PsiClass containingClass, @NotNull PsiAnnotation fieldNameConstants) {
    String typeName = PsiAnnotationUtil.getStringAnnotationValue(fieldNameConstants, "innerTypeName", "");
    if (StringUtil.isEmptyOrSpaces(typeName)) {
      final ConfigDiscovery configDiscovery = ConfigDiscovery.getInstance();
      typeName = configDiscovery.getStringLombokConfigProperty(ConfigKey.FIELD_NAME_CONSTANTS_TYPENAME, containingClass);
    }
    return typeName;
  }

  @Nullable
  public static LombokLightClassBuilder createInnerClassOrEnum(@NotNull String name, @NotNull PsiClass containingClass, @NotNull PsiAnnotation psiAnnotation) {
    final String accessLevel = LombokProcessorUtil.getLevelVisibility(psiAnnotation);
    if (accessLevel == null) {
      return null;
    }
    final boolean asEnum = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "asEnum", false);
    if (asEnum) {
      return createEnum(name, containingClass, accessLevel, psiAnnotation);
    } else {
      return createInnerClass(name, containingClass, accessLevel, psiAnnotation);
    }
  }

  private static boolean useUppercasedConstants(@NotNull PsiClass containingClass) {
    final ConfigDiscovery configDiscovery = ConfigDiscovery.getInstance();
    return configDiscovery.getBooleanLombokConfigProperty(ConfigKey.FIELD_NAME_CONSTANTS_UPPERCASE, containingClass);
  }

  public static List<PsiField> createFields(@NotNull PsiClass containingClass, @NotNull Collection<PsiMember> psiMembers) {
    final Set<String> existingFieldNames = PsiClassUtil.collectClassFieldsIntern(containingClass).stream().map(PsiField::getName).collect(Collectors.toSet());
    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(containingClass.getProject());
    final PsiClassType classType = psiElementFactory.createType(containingClass);
    boolean makeUppercased = useUppercasedConstants(containingClass);
    return psiMembers.stream()
      .filter(psiMember -> !existingFieldNames.contains(makeFieldNameConstant(psiMember, makeUppercased)))
      .map(psiField -> {
        if (containingClass.isEnum()) {
          return createEnumConstant(psiField, makeUppercased, containingClass, classType);
        }
        return createFieldNameConstant(psiField, makeUppercased, containingClass);
      }).collect(Collectors.toList());
  }

  @NotNull
  private static LombokLightClassBuilder createEnum(@NotNull String name, @NotNull PsiClass containingClass, @NotNull String accessLevel, @NotNull PsiElement navigationElement) {
    final String innerClassQualifiedName = containingClass.getQualifiedName() + "." + name;
    final LombokLightClassBuilder lazyClassBuilder = new LombokLightClassBuilder(containingClass, name, innerClassQualifiedName);
    lazyClassBuilder.withContainingClass(containingClass)
      .withNavigationElement(navigationElement)
      .withEnum(true)
      .withModifier(accessLevel)
      .withImplicitModifier(PsiModifier.STATIC)
      .withImplicitModifier(PsiModifier.FINAL);

    lazyClassBuilder.withMethodSupplier((thisPsiClass)-> {
      //add enum methods like here:  ClassInnerStuffCache.calcMethods
      final PsiManager psiManager = containingClass.getManager();
      final PsiClassType enumClassType = PsiClassUtil.getTypeWithGenerics(thisPsiClass);
//    "public static " + myClass.getName() + "[] values() { }"
      final LombokLightMethodBuilder valuesEnumMethod = new LombokLightMethodBuilder(psiManager, "values")
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC)
        .withContainingClass(containingClass)
        .withNavigationElement(navigationElement)
        .withMethodReturnType(new PsiArrayType(enumClassType))
        .withBodyText("");

      //     "public static " + myClass.getName() + " valueOf(java.lang.String name) throws java.lang.IllegalArgumentException { }"
      final LombokLightMethodBuilder valueOfEnumMethod = new LombokLightMethodBuilder(psiManager, "valueOf")
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC)
        .withContainingClass(containingClass)
        .withNavigationElement(navigationElement)
        .withParameter("name", PsiType.getJavaLangString(psiManager, containingClass.getResolveScope()))
        .withException(PsiType.getTypeByName("java.lang.IllegalArgumentException", containingClass.getProject(), containingClass.getResolveScope()))
        .withMethodReturnType(enumClassType)
        .withBodyText("");

      return List.of(valuesEnumMethod, valueOfEnumMethod);
    });

    return lazyClassBuilder;
  }

  private static PsiField createEnumConstant(@NotNull PsiMember psiMember, boolean makeUppercased, @NotNull PsiClass containingClass, PsiClassType classType) {
    return new LombokEnumConstantBuilder(containingClass.getManager(), makeFieldNameConstant(psiMember, makeUppercased), classType)
      .withContainingClass(containingClass)
      .withModifier(PsiModifier.PUBLIC)
      .withImplicitModifier(PsiModifier.STATIC)
      .withImplicitModifier(PsiModifier.FINAL)
      .withNavigationElement(psiMember);
  }

  private static String makeFieldNameConstant(@NotNull PsiMember psiMember, boolean makeUppercased) {
    final String fieldName = psiMember.getName();
    return makeUppercased ? LombokUtils.camelCaseToConstant(fieldName) : fieldName;
  }

  @NotNull
  private static LombokLightClassBuilder createInnerClass(@NotNull String name, @NotNull PsiClass containingClass, @NotNull String accessLevel, @NotNull PsiElement navigationElement) {
    final String innerClassQualifiedName = containingClass.getQualifiedName() + "." + name;
    final LombokLightClassBuilder lazyClassBuilder = new LombokLightClassBuilder(containingClass, name, innerClassQualifiedName);
    lazyClassBuilder.withContainingClass(containingClass)
      .withNavigationElement(navigationElement)
      .withModifier(accessLevel)
      .withModifier(PsiModifier.STATIC)
      .withModifier(PsiModifier.FINAL);
    return lazyClassBuilder;
  }

  @NotNull
  private static PsiField createFieldNameConstant(@NotNull PsiMember psiMember, boolean makeUppercased, @NotNull PsiClass containingClass) {
    final PsiManager manager = containingClass.getContainingFile().getManager();
    final PsiType fieldNameConstType = PsiType.getJavaLangString(manager, GlobalSearchScope.allScope(containingClass.getProject()));

    LombokLightFieldBuilder fieldNameConstant = new LombokLightFieldBuilder(manager, makeFieldNameConstant(psiMember, makeUppercased), fieldNameConstType)
      .withContainingClass(containingClass)
      .withNavigationElement(psiMember)
      .withModifier(PsiModifier.PUBLIC)
      .withModifier(PsiModifier.STATIC)
      .withModifier(PsiModifier.FINAL);

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(containingClass.getProject());
    final PsiExpression initializer = psiElementFactory.createExpressionFromText("\"" + psiMember.getName() + "\"", containingClass);
    fieldNameConstant.setInitializer(initializer);
    return fieldNameConstant;
  }
}
