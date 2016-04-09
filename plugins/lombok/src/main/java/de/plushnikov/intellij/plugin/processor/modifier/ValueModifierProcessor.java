package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexej Kubarev
 */
public class ValueModifierProcessor implements ModifierProcessor {


  @Override
  @SuppressWarnings("unchecked")
  public boolean isSupported(@NotNull PsiModifierList modifierList, @NotNull String name) {

    // @Value makes things final and private, everything else is to be skipped quickly
    if (!PsiModifier.FINAL.equals(name) || !PsiModifier.PRIVATE.equals(name)) {
      return false;
    }

    PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierList, PsiClass.class, true);

    return null != searchableClass && PsiAnnotationSearchUtil.isAnnotatedWith(searchableClass, lombok.Value.class, lombok.experimental.Value.class);

  }

  @Override
  public Boolean hasModifierProperty(@NotNull PsiModifierList modifierList, @NotNull String name) {

    //TODO: Take care of @Wither, and similar

    if (PsiModifier.FINAL.equals(name)) {
      return Boolean.TRUE;
    }

    if (PsiModifier.PRIVATE.equals(name)) {
      if (modifierList.getParent() instanceof PsiField) {
        return Boolean.TRUE;
      }
    }

    return null;
  }
}
