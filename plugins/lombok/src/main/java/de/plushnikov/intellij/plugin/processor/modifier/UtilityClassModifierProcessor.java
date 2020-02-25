package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.problem.ProblemNewBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.UtilityClassProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Florian Böhm
 */
public class UtilityClassModifierProcessor implements ModifierProcessor {

  public static boolean isModifierListSupported(@NotNull PsiModifierList modifierList) {
    PsiElement modifierListParent = modifierList.getParent();

    if (modifierListParent instanceof PsiClass) {
      PsiClass parentClass = (PsiClass) modifierListParent;
      if (PsiAnnotationSearchUtil.isAnnotatedWith(parentClass, UtilityClass.class)) {
        return UtilityClassProcessor.validateOnRightType(parentClass, new ProblemNewBuilder());
      }
    }

    if (!isElementFieldOrMethodOrInnerClass(modifierListParent)) {
      return false;
    }

    PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierListParent, PsiClass.class, true);

    return null != searchableClass && PsiAnnotationSearchUtil.isAnnotatedWith(searchableClass, UtilityClass.class) && UtilityClassProcessor.validateOnRightType(searchableClass, new ProblemNewBuilder());
  }

  @Override
  public boolean isSupported(@NotNull PsiModifierList modifierList) {
    return isModifierListSupported(modifierList);
  }

  @Override
  public void transformModifiers(@NotNull PsiModifierList modifierList, @NotNull final Set<String> modifiers) {
    final PsiElement parent = modifierList.getParent();

    // FINAL
    if (parent instanceof PsiClass) {
      PsiClass psiClass = (PsiClass) parent;
      if (PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, UtilityClass.class)) {
        modifiers.add(PsiModifier.FINAL);
      }
    }

    // STATIC
    if (isElementFieldOrMethodOrInnerClass(parent)) {
      modifiers.add(PsiModifier.STATIC);
    }
  }

  private static boolean isElementFieldOrMethodOrInnerClass(PsiElement element) {
    return element instanceof PsiField || element instanceof PsiMethod ||
      (element instanceof PsiClass && element.getParent() instanceof PsiClass && !((PsiClass) element.getParent()).isInterface());
  }
}
