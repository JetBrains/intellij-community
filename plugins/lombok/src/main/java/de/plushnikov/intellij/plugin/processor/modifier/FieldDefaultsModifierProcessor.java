package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.AccessLevel;
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

  private final ConfigDiscovery configDiscovery;

  public FieldDefaultsModifierProcessor(@NotNull ConfigDiscovery configDiscovery) {
    this.configDiscovery = configDiscovery;
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

    @Nullable final PsiAnnotation fieldDefaultsAnnotation = PsiAnnotationSearchUtil.findAnnotation(searchableClass, lombok.experimental.FieldDefaults.class);
    final boolean isConfigDefaultFinal = isConfigDefaultFinal(searchableClass);
    final boolean isConfigDefaultPrivate = isConfigDefaultPrivate(searchableClass);

    final PsiField parentField = (PsiField) modifierList.getParent();

    // FINAL
    if (shouldMakeFinal(parentField, fieldDefaultsAnnotation, isConfigDefaultFinal)) {
      modifiers.add(PsiModifier.FINAL);
    }

    // VISIBILITY
    if (canChangeVisibility(parentField, modifierList)) {
      final AccessLevel defaultAccessLevel = detectDefaultAccessLevel(fieldDefaultsAnnotation, isConfigDefaultPrivate);
      switch (defaultAccessLevel) {
        case PRIVATE:
          modifiers.add(PsiModifier.PRIVATE);
          modifiers.remove(PsiModifier.PACKAGE_LOCAL);
          break;
        case PROTECTED:
          modifiers.add(PsiModifier.PROTECTED);
          modifiers.remove(PsiModifier.PACKAGE_LOCAL);
          break;
        case PUBLIC:
          modifiers.add(PsiModifier.PUBLIC);
          modifiers.remove(PsiModifier.PACKAGE_LOCAL);
          break;
      }
    }
  }

  private boolean canBeAffected(PsiClass searchableClass) {
    return PsiAnnotationSearchUtil.isAnnotatedWith(searchableClass, lombok.experimental.FieldDefaults.class)
      || isConfigDefaultFinal(searchableClass)
      || isConfigDefaultPrivate(searchableClass);
  }

  private boolean isConfigDefaultFinal(PsiClass searchableClass) {
    return configDiscovery.getBooleanLombokConfigProperty(ConfigKey.FIELDDEFAULTS_FINAL, searchableClass);
  }

  private boolean isConfigDefaultPrivate(PsiClass searchableClass) {
    return configDiscovery.getBooleanLombokConfigProperty(ConfigKey.FIELDDEFAULTS_PRIVATE, searchableClass);
  }

  private boolean shouldMakeFinal(@NotNull PsiField parentField, @Nullable PsiAnnotation fieldDefaultsAnnotation, boolean isConfigDefaultFinal) {
    return shouldMakeFinalByDefault(fieldDefaultsAnnotation, isConfigDefaultFinal)
      && !PsiAnnotationSearchUtil.isAnnotatedWith(parentField, lombok.experimental.NonFinal.class);
  }

  private boolean shouldMakeFinalByDefault(@Nullable PsiAnnotation fieldDefaultsAnnotation, boolean isConfigDefaultFinal) {
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
  private boolean canChangeVisibility(@NotNull PsiField parentField, @NotNull PsiModifierList modifierList) {
    return !hasExplicitAccessModifier(modifierList)
      && !PsiAnnotationSearchUtil.isAnnotatedWith(parentField, lombok.experimental.PackagePrivate.class);
  }

  private AccessLevel detectDefaultAccessLevel(@Nullable PsiAnnotation fieldDefaultsAnnotation, boolean isConfigDefaultPrivate) {
    final AccessLevel accessLevelFromAnnotation = fieldDefaultsAnnotation != null
      ? LombokProcessorUtil.getAccessLevel(fieldDefaultsAnnotation, "level")
      : AccessLevel.NONE;

    if (accessLevelFromAnnotation == AccessLevel.NONE && isConfigDefaultPrivate) {
      return AccessLevel.PRIVATE;
    }
    return accessLevelFromAnnotation;
  }

  private boolean hasExplicitAccessModifier(@NotNull PsiModifierList modifierList) {
    return modifierList.hasExplicitModifier(PsiModifier.PUBLIC)
      || modifierList.hasExplicitModifier(PsiModifier.PRIVATE)
      || modifierList.hasExplicitModifier(PsiModifier.PROTECTED);
  }
}
