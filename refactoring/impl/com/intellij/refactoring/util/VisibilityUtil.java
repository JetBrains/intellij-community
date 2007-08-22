/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.06.2002
 * Time: 18:48:01
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;


public class VisibilityUtil  {
  @NonNls public static final String ESCALATE_VISIBILITY = "EscalateVisible";
  private static final String[] visibilityModifiers = {
    PsiModifier.PRIVATE,
    PsiModifier.PACKAGE_LOCAL,
    PsiModifier.PROTECTED,
    PsiModifier.PUBLIC
  };

  private VisibilityUtil() {
  }

  public static String getHighestVisibility(String v1, String v2) {
    if(v1.equals(v2)) return v1;

    if(PsiModifier.PRIVATE.equals(v1)) return v2;
    if(PsiModifier.PUBLIC.equals(v1)) return PsiModifier.PUBLIC;
    if(PsiModifier.PRIVATE.equals(v2)) return v1;
    if(PsiModifier.PUBLIC.equals(v2)) return PsiModifier.PUBLIC;

    return PsiModifier.PUBLIC;
  }

  public static void escalateVisibility(PsiMember modifierListOwner, PsiElement place) throws IncorrectOperationException {
    final String visibilityModifier = getVisibilityModifier(modifierListOwner.getModifierList());
    int index;
    for (index = 0; index < visibilityModifiers.length; index++) {
      String modifier = visibilityModifiers[index];
      if(modifier.equals(visibilityModifier)) break;
    }
    for(;index < visibilityModifiers.length && !PsiUtil.isAccessible(modifierListOwner, place, null); index++) {
      modifierListOwner.getModifierList().setModifierProperty(visibilityModifiers[index], true);
    }
  }

  public static String getPossibleVisibility(final PsiMember psiMethod, final PsiElement place) {
    if (PsiUtil.isAccessible(psiMethod, place, null)) return getVisibilityStringToDisplay(psiMethod);
    if (psiMethod.getManager().arePackagesTheSame(psiMethod, place)) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (InheritanceUtil.isInheritorOrSelf(PsiTreeUtil.getParentOfType(place, PsiClass.class),
                                          psiMethod.getContainingClass(), true)) {
      return PsiModifier.PROTECTED;
    }
    return PsiModifier.PUBLIC;
  }

  public static String getVisibilityModifier(PsiModifierList list) {
    if (list == null) return PsiModifier.PACKAGE_LOCAL;
    for (String modifier : visibilityModifiers) {
      if (list.hasModifierProperty(modifier)) {
        return modifier;
      }
    }
    return PsiModifier.PACKAGE_LOCAL;
  }

  public static String getVisibilityString(String visibilityModifier) {
    if(PsiModifier.PACKAGE_LOCAL.equals(visibilityModifier)) {
      return "";
    }
    else return visibilityModifier;
  }

  public static @NonNls String getVisibilityStringToDisplay(PsiMember member) {
    if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
      return "public";
    }
    if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
      return "protected";
    }
    if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
      return "private";
    }
    return "package local";
  }
}
