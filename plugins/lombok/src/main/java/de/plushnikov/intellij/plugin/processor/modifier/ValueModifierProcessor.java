package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Processor for {@literal @Value} feature of Lombok.
 * @author Alexej Kubarev
 */
public class ValueModifierProcessor implements ModifierProcessor {

  @Override
  public boolean isSupported(@NotNull PsiModifierList modifierList) {

    final PsiElement modifierListParent = modifierList.getParent();

    if (!(modifierListParent instanceof PsiField || modifierListParent instanceof PsiClass)) {
      return false;
    }

    PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierList, PsiClass.class, true);

    return null != searchableClass && PsiAnnotationSearchUtil.isAnnotatedWith(searchableClass, LombokClassNames.VALUE);
  }

  @Override
  public void transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
    if (modifiers.contains(PsiModifier.STATIC)) {
      return; // skip static fields
    }

    final PsiModifierListOwner parentElement = PsiTreeUtil.getParentOfType(modifierList, PsiModifierListOwner.class, false);
    if (null != parentElement) {

      // FINAL
      if (!PsiAnnotationSearchUtil.isAnnotatedWith(parentElement, LombokClassNames.NON_FINAL)) {
        modifiers.add(PsiModifier.FINAL);
      }

      // PRIVATE
      if (modifierList.getParent() instanceof PsiField &&
        // Visibility is only changed for package private fields
        hasPackagePrivateModifier(modifierList) &&
        // except they are annotated with @PackagePrivate
        !PsiAnnotationSearchUtil.isAnnotatedWith(parentElement, LombokClassNames.PACKAGE_PRIVATE)) {
        modifiers.add(PsiModifier.PRIVATE);

        // IDEA _right now_ checks if other modifiers are set, and ignores PACKAGE_LOCAL but may as well clean it up
        modifiers.remove(PsiModifier.PACKAGE_LOCAL);
      }
    }
  }

  private static boolean hasPackagePrivateModifier(@NotNull PsiModifierList modifierList) {
    return !(modifierList.hasExplicitModifier(PsiModifier.PUBLIC) || modifierList.hasExplicitModifier(PsiModifier.PRIVATE) ||
      modifierList.hasExplicitModifier(PsiModifier.PROTECTED));
  }
}
