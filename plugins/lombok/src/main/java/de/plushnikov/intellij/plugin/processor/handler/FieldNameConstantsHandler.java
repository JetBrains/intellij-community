package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.psi.LombokEnumConstantBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class FieldNameConstantsHandler {

  @Nullable
  public static PsiClass createInnerClassOrEnum(@NotNull PsiClass containingClass, @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<PsiField> psiFIelds) {
    final String accessLevel = LombokProcessorUtil.getLevelVisibility(psiAnnotation);
    if (accessLevel == null) {
      return null;
    }
    String typeName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "innerTypeName");
    if (typeName == null || typeName.equals("")) {
      final ConfigDiscovery configDiscovery = ConfigDiscovery.getInstance();
      typeName = configDiscovery.getStringLombokConfigProperty(ConfigKey.FIELD_NAME_CONSTANTS_TYPENAME, containingClass);
    }
    final boolean asEnum = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "asEnum", false);
    if (asEnum) {
      return createEnum(typeName, containingClass, psiFIelds, accessLevel, psiAnnotation);
    } else {
      return createInnerClass(typeName, containingClass, psiFIelds, accessLevel, psiAnnotation);
    }
  }

  @NotNull
  private static PsiClass createEnum(@NotNull String name, @NotNull PsiClass containingClass, @NotNull Collection<PsiField> fields, @NotNull String accessLevel, @NotNull PsiElement navigationElement) {
    final String innerClassQualifiedName = containingClass.getQualifiedName() + "." + name;
    final LombokLightClassBuilder classBuilder = new LombokLightClassBuilder(containingClass, name, innerClassQualifiedName);
    classBuilder.withContainingClass(containingClass)
      .withNavigationElement(navigationElement)
      .withEnum(true)
      .withModifier(accessLevel)
      .withImplicitModifier(PsiModifier.STATIC)
      .withImplicitModifier(PsiModifier.FINAL);

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(containingClass.getProject());
    final PsiClassType classType = psiElementFactory.createType(classBuilder);
    fields.forEach(field -> {
      final LombokLightFieldBuilder enumConstantBuilder = new LombokEnumConstantBuilder(containingClass.getManager(), field.getName(), classType)
        .withContainingClass(containingClass)
        .withModifier(PsiModifier.PUBLIC)
        .withImplicitModifier(PsiModifier.STATIC)
        .withImplicitModifier(PsiModifier.FINAL)
        .withNavigationElement(field);
      classBuilder.withField(enumConstantBuilder);
    });
    return classBuilder;
  }

  @NotNull
  private static PsiClass createInnerClass(@NotNull String name, @NotNull PsiClass containingClass, @NotNull Collection<PsiField> psiFields, @NotNull String accessLevel, @NotNull PsiElement navigationElement) {
    final String innerClassQualifiedName = containingClass.getQualifiedName() + "." + name;
    final LombokLightClassBuilder classBuilder = new LombokLightClassBuilder(containingClass, name, innerClassQualifiedName);
    classBuilder.withContainingClass(containingClass)
      .withNavigationElement(navigationElement)
      .withModifier(accessLevel)
      .withModifier(PsiModifier.STATIC)
      .withModifier(PsiModifier.FINAL);
    psiFields.stream().map(psiField -> createFieldNameConstant(psiField, classBuilder)).forEach(classBuilder::withField);
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
