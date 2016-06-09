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

/**
 * @author Florian Böhm
 */
public class UtilityClassModifierProcessor implements ModifierProcessor {

  @Override
  public boolean isSupported(@NotNull PsiModifierList modifierList, @NotNull String name) {

    if(!PsiModifier.STATIC.equals(name) && !PsiModifier.FINAL.equals(name)) {
      return false;
    }

    PsiElement modifierListParent = modifierList.getParent();

    if(PsiModifier.FINAL.equals(name)) {
      if(modifierListParent instanceof PsiClass) {
        PsiClass parentClass = (PsiClass) modifierListParent;
        if(PsiAnnotationSearchUtil.isAnnotatedWith(parentClass, UtilityClass.class)) {
          return UtilityClassProcessor.validateOnRightType(parentClass, new ProblemNewBuilder());
        }
      }
    }

    if(!isElementFieldMethodOrInnerClass(modifierListParent)) {
      return false;
    }

    PsiClass searchableClass = PsiTreeUtil.getParentOfType(modifierListParent, PsiClass.class, true);

    return null != searchableClass && PsiAnnotationSearchUtil.isAnnotatedWith(searchableClass, UtilityClass.class) && UtilityClassProcessor.validateOnRightType(searchableClass, new ProblemNewBuilder());
  }

  public Boolean hasModifierProperty(@NotNull PsiModifierList psiModifierList, @NotNull String name) {

    if(PsiModifier.FINAL.equals(name)) {
      PsiElement parent = psiModifierList.getParent();
      if(parent instanceof PsiClass) {
        PsiClass psiClass = (PsiClass) parent;
        if(PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, UtilityClass.class)) {
          return Boolean.TRUE;
        }
      }
      return null;
    }
    return Boolean.TRUE;
  }

  private boolean isElementFieldMethodOrInnerClass(PsiElement element) {
    return element instanceof PsiField || element instanceof PsiMethod || (element instanceof PsiClass && element.getParent() instanceof PsiClass);
  }
}