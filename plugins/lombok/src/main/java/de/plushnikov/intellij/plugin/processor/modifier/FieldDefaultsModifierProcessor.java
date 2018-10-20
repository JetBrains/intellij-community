package de.plushnikov.intellij.plugin.processor.modifier;

import com.google.common.collect.Iterables;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.AccessLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * Processor for <strong>experimental</strong> {@literal @FieldDefaults} feature of Lombok.
 *
 * @author Alexej Kubarev
 * @see <a href="https://projectlombok.org/features/experimental/FieldDefaults.html">Lombok Feature: Field Defaults</a>
 */
public class FieldDefaultsModifierProcessor implements ModifierProcessor {

  @Override
  public boolean isSupported(@NotNull PsiModifierList modifierList) {

    // FieldDefaults only change modifiers of class fields
    // but nor for enum constants or lombok generated fields
    final PsiElement psiElement = modifierList.getParent();
    if (!(psiElement instanceof PsiField) || psiElement instanceof PsiEnumConstant || psiElement instanceof LombokLightFieldBuilder) {
      return false;
    }

    PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierList, PsiClass.class, true);

    return null != searchableClass && PsiAnnotationSearchUtil.isAnnotatedWith(searchableClass, lombok.experimental.FieldDefaults.class);
  }

  @Override
  public void transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
    if (modifiers.contains(PsiModifier.STATIC)) {
      return; // skip static fields
    }

    PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierList, PsiClass.class, true);
    if (searchableClass == null) {
      return; // Should not get here, but safer to check
    }

    PsiAnnotation fieldDefaultsAnnotation = PsiAnnotationSearchUtil.findAnnotation(searchableClass, lombok.experimental.FieldDefaults.class);
    if (fieldDefaultsAnnotation == null) {
      return; // Should not get here, but safer to check
    }

    final PsiField parentElement = (PsiField) modifierList.getParent();

    // FINAL
    // Is @FieldDefaults(makeFinal = true)?
    if ((PsiAnnotationUtil.getBooleanAnnotationValue(fieldDefaultsAnnotation, "makeFinal", false)) &&
      (!PsiAnnotationSearchUtil.isAnnotatedWith(parentElement, lombok.experimental.NonFinal.class))) {
      modifiers.add(PsiModifier.FINAL);
    }

    // VISIBILITY
    Collection<String> defaultLevels = PsiAnnotationUtil.getAnnotationValues(fieldDefaultsAnnotation, "level", String.class);
    final AccessLevel defaultAccessLevel = AccessLevel.valueOf(Iterables.getFirst(defaultLevels, AccessLevel.NONE.name()));

    if (// If explicit visibility modifier is set - no point to continue.
      !hasPackagePrivateModifier(modifierList) ||
        // If @PackagePrivate is requested, leave the field as is
        PsiAnnotationSearchUtil.isAnnotatedWith(parentElement, lombok.experimental.PackagePrivate.class)) {
      return;
    }

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
      default:
        break;
    }
  }

  private boolean hasPackagePrivateModifier(@NotNull PsiModifierList modifierList) {
    return !(modifierList.hasExplicitModifier(PsiModifier.PUBLIC) || modifierList.hasExplicitModifier(PsiModifier.PRIVATE) ||
      modifierList.hasExplicitModifier(PsiModifier.PROTECTED));
  }
}
