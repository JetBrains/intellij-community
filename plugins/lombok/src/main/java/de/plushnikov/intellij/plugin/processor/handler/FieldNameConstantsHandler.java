package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.psi.LombokEnumConstantBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FieldNameConstantsHandler {

  public static String getTypeName(@NotNull PsiClass containingClass, @NotNull PsiAnnotation fieldNameConstants) {
    String typeName = PsiAnnotationUtil.getStringAnnotationValue(fieldNameConstants, "innerTypeName");
    if (typeName == null || typeName.equals("")) {
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

  public static List<PsiField> createFields(@NotNull PsiClass containingClass, @NotNull Collection<PsiField> psiFields) {
    final Set<String> existingFieldNames = PsiClassUtil.collectClassFieldsIntern(containingClass).stream().map(PsiField::getName).collect(Collectors.toSet());
    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(containingClass.getProject());
    final PsiClassType classType = psiElementFactory.createType(containingClass);
    return psiFields.stream().filter(psiField -> !existingFieldNames.contains(psiField.getName()))
      .map(psiField -> {
        if (containingClass.isEnum()) {
          return createEnumConstant(psiField, containingClass, classType);
        }
        return createFieldNameConstant(psiField, containingClass);
      }).collect(Collectors.toList());
  }

  @NotNull
  private static LombokLightClassBuilder createEnum(@NotNull String name, @NotNull PsiClass containingClass, @NotNull String accessLevel, @NotNull PsiElement navigationElement) {
    final String innerClassQualifiedName = containingClass.getQualifiedName() + "." + name;
    final LombokLightClassBuilder classBuilder = new LombokLightClassBuilder(containingClass, name, innerClassQualifiedName);
    classBuilder.withContainingClass(containingClass)
      .withNavigationElement(navigationElement)
      .withEnum(true)
      .withModifier(accessLevel)
      .withImplicitModifier(PsiModifier.STATIC)
      .withImplicitModifier(PsiModifier.FINAL);
    return classBuilder;
  }

  private static PsiField createEnumConstant(@NotNull PsiField field, @NotNull PsiClass containingClass, PsiClassType classType) {
    final LombokLightFieldBuilder enumConstantBuilder = new LombokEnumConstantBuilder(containingClass.getManager(), field.getName(), classType)
      .withContainingClass(containingClass)
      .withModifier(PsiModifier.PUBLIC)
      .withImplicitModifier(PsiModifier.STATIC)
      .withImplicitModifier(PsiModifier.FINAL)
      .withNavigationElement(field);
    return enumConstantBuilder;
  }

  @NotNull
  private static LombokLightClassBuilder createInnerClass(@NotNull String name, @NotNull PsiClass containingClass, @NotNull String accessLevel, @NotNull PsiElement navigationElement) {
    final String innerClassQualifiedName = containingClass.getQualifiedName() + "." + name;
    final LombokLightClassBuilder classBuilder = new LombokLightClassBuilder(containingClass, name, innerClassQualifiedName);
    classBuilder.withContainingClass(containingClass)
      .withNavigationElement(navigationElement)
      .withModifier(accessLevel)
      .withModifier(PsiModifier.STATIC)
      .withModifier(PsiModifier.FINAL);
    return classBuilder;
  }

  @NotNull
  private static PsiField createFieldNameConstant(@NotNull PsiField psiField, @NotNull PsiClass containingClass) {
    final PsiManager manager = containingClass.getContainingFile().getManager();
    final PsiType fieldNameConstType = PsiType.getJavaLangString(manager, GlobalSearchScope.allScope(containingClass.getProject()));

    LombokLightFieldBuilder fieldNameConstant = new LombokLightFieldBuilder(manager, psiField.getName(), fieldNameConstType)
      .withContainingClass(containingClass)
      .withNavigationElement(psiField)
      .withModifier(PsiModifier.PUBLIC)
      .withModifier(PsiModifier.STATIC)
      .withModifier(PsiModifier.FINAL);

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(containingClass.getProject());
    final PsiExpression initializer = psiElementFactory.createExpressionFromText("\"" + psiField.getName() + "\"", containingClass);
    fieldNameConstant.setInitializer(initializer);
    return fieldNameConstant;
  }
}
