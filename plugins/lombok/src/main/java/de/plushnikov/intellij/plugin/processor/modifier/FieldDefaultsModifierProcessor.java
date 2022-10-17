package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Processor for <strong>experimental</strong> {@literal @FieldDefaults} feature of Lombok.
 *
 * @author Alexej Kubarev
 * @author Tomasz Linkowski
 * @see <a href="https://projectlombok.org/features/experimental/FieldDefaults.html">Lombok Feature: Field Defaults</a>
 */
public class FieldDefaultsModifierProcessor implements ModifierProcessor {

  private static ConfigDiscovery getConfigDiscovery() {
    return ApplicationManager.getApplication().getService(ConfigDiscovery.class);
  }

  @Override
  public boolean isSupported(@NotNull PsiModifierList modifierList) {
    // FieldDefaults only change modifiers of class fields
    // but not for enum constants or lombok generated fields
    final PsiElement psiElement = modifierList.getParent();
    if (!(psiElement instanceof PsiField) || psiElement instanceof PsiEnumConstant || psiElement instanceof LombokLightFieldBuilder) {
      return false;
    }

    final PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierList, PsiClass.class, true);

    return null != searchableClass && canBeAffected(searchableClass);
  }

  @Override
  public void transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
    if (modifiers.contains(PsiModifier.STATIC) || UtilityClassModifierProcessor.isModifierListSupported(modifierList)) {
      return; // skip static fields
    }

    final PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierList, PsiClass.class, true);
    if (searchableClass == null) {
      return; // Should not get here, but safer to check
    }

    @Nullable final PsiAnnotation fieldDefaultsAnnotation = PsiAnnotationSearchUtil.findAnnotation(searchableClass,
                                                                                                   LombokClassNames.FIELD_DEFAULTS);
    final boolean isConfigDefaultFinal = isConfigDefaultFinal(searchableClass);
    final boolean isConfigDefaultPrivate = isConfigDefaultPrivate(searchableClass);

    final PsiField parentField = (PsiField) modifierList.getParent();

    // FINAL
    if (shouldMakeFinal(parentField, fieldDefaultsAnnotation, isConfigDefaultFinal)) {
      modifiers.add(PsiModifier.FINAL);
    }

    // VISIBILITY
    if (canChangeVisibility(parentField, modifierList)) {
      final String defaultAccessLevel = detectDefaultAccessLevel(fieldDefaultsAnnotation, isConfigDefaultPrivate);
      if (PsiModifier.PRIVATE.equals(defaultAccessLevel)) {
        modifiers.add(PsiModifier.PRIVATE);
        modifiers.remove(PsiModifier.PACKAGE_LOCAL);
      }
      else if (PsiModifier.PROTECTED.equals(defaultAccessLevel)) {
        modifiers.add(PsiModifier.PROTECTED);
        modifiers.remove(PsiModifier.PACKAGE_LOCAL);
      }
      else if (PsiModifier.PUBLIC.equals(defaultAccessLevel)) {
        modifiers.add(PsiModifier.PUBLIC);
        modifiers.remove(PsiModifier.PACKAGE_LOCAL);
      }
    }
  }

  private static boolean canBeAffected(PsiClass searchableClass) {
    return PsiAnnotationSearchUtil.isAnnotatedWith(searchableClass, LombokClassNames.FIELD_DEFAULTS)
      || isConfigDefaultFinal(searchableClass)
      || isConfigDefaultPrivate(searchableClass);
  }

  private static boolean isConfigDefaultFinal(PsiClass searchableClass) {
    return getConfigDiscovery().getBooleanLombokConfigProperty(ConfigKey.FIELDDEFAULTS_FINAL, searchableClass);
  }

  private static boolean isConfigDefaultPrivate(PsiClass searchableClass) {
    return getConfigDiscovery().getBooleanLombokConfigProperty(ConfigKey.FIELDDEFAULTS_PRIVATE, searchableClass);
  }

  private static boolean shouldMakeFinal(@NotNull PsiField parentField,
                                         @Nullable PsiAnnotation fieldDefaultsAnnotation,
                                         boolean isConfigDefaultFinal) {
    return shouldMakeFinalByDefault(fieldDefaultsAnnotation, isConfigDefaultFinal)
      && !PsiAnnotationSearchUtil.isAnnotatedWith(parentField, LombokClassNames.NON_FINAL);
  }

  private static boolean shouldMakeFinalByDefault(@Nullable PsiAnnotation fieldDefaultsAnnotation, boolean isConfigDefaultFinal) {
    if (fieldDefaultsAnnotation != null) {
      // Is @FieldDefaults(makeFinal = true)?
      return PsiAnnotationUtil.getBooleanAnnotationValue(fieldDefaultsAnnotation, "makeFinal", false);
    }
    return isConfigDefaultFinal;
  }

  /**
   * If explicit visibility modifier is set - no point to continue.
   * If @PackagePrivate is requested, leave the field as is.
   */
  private static boolean canChangeVisibility(@NotNull PsiField parentField, @NotNull PsiModifierList modifierList) {
    return !hasExplicitAccessModifier(modifierList)
      && !PsiAnnotationSearchUtil.isAnnotatedWith(parentField, LombokClassNames.PACKAGE_PRIVATE);
  }

  private static String detectDefaultAccessLevel(@Nullable PsiAnnotation fieldDefaultsAnnotation, boolean isConfigDefaultPrivate) {
    final String accessLevelFromAnnotation = fieldDefaultsAnnotation != null
      ? LombokProcessorUtil.getAccessLevel(fieldDefaultsAnnotation, "level")
      : null;

    if (accessLevelFromAnnotation == null && isConfigDefaultPrivate) {
      return PsiModifier.PRIVATE;
    }
    return accessLevelFromAnnotation;
  }

  private static boolean hasExplicitAccessModifier(@NotNull PsiModifierList modifierList) {
    return modifierList.hasExplicitModifier(PsiModifier.PUBLIC)
      || modifierList.hasExplicitModifier(PsiModifier.PRIVATE)
      || modifierList.hasExplicitModifier(PsiModifier.PROTECTED);
  }
}
