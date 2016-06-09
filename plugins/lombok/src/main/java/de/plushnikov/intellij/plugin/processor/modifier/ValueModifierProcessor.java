package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;

/**
 * @author Alexej Kubarev
 */
public class ValueModifierProcessor implements ModifierProcessor {

  @Override
  @SuppressWarnings("unchecked")
  public boolean isSupported(@NotNull PsiModifierList modifierList) {

    // @Value only makes fields and class final, methods are to be skipped
    final PsiElement modifierListParent = modifierList.getParent();
    if (!(modifierListParent instanceof PsiField || modifierListParent instanceof PsiClass)) {
      return false;
    }

    PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierList, PsiClass.class, true);

    return null != searchableClass && PsiAnnotationSearchUtil.isAnnotatedWith(searchableClass, lombok.Value.class, lombok.experimental.Value.class);
  }

  @Override
  @NotNull
  public Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {

    final PsiModifierListOwner parentElement = PsiTreeUtil.getParentOfType(modifierList, PsiModifierListOwner.class, false);
    if (null != parentElement) {

      // FINAL

        if (!PsiAnnotationSearchUtil.isAnnotatedWith(parentElement, lombok.experimental.NonFinal.class)) {
          modifiers.add(PsiModifier.FINAL);
        }


      // PRIVATE
        if (modifierList.getParent() instanceof PsiField &&
            // Visibility is only changed for package private fields
            hasPackagePrivateModifier(modifierList) &&
            // except they are annotated with @PackagePrivate
            !PsiAnnotationSearchUtil.isAnnotatedWith(parentElement, lombok.experimental.PackagePrivate.class)) {
          modifiers.add(PsiModifier.PRIVATE);
      }
    }

    return modifiers;
  };

  private boolean hasPackagePrivateModifier(@NotNull PsiModifierList modifierList) {
    return !(modifierList.hasExplicitModifier(PsiModifier.PUBLIC) || modifierList.hasExplicitModifier(PsiModifier.PRIVATE) ||
        modifierList.hasExplicitModifier(PsiModifier.PROTECTED));
  }
}
