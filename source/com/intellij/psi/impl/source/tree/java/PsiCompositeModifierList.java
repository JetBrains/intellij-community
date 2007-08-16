/*
 * @author max
 */
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.light.LightModifierList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PsiCompositeModifierList extends LightModifierList {
  private final List<PsiModifierList> mySublists;

  public PsiCompositeModifierList(final PsiManager manager, List<PsiModifierList> sublists) {
    super(manager);
    mySublists = sublists;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    List<PsiAnnotation> annotations = new ArrayList<PsiAnnotation>();
    for (PsiModifierList list : mySublists) {
      annotations.addAll(Arrays.asList(list.getAnnotations()));
    }
    return annotations.toArray(new PsiAnnotation[annotations.size()]);
  }

  public PsiAnnotation findAnnotation(@NotNull final String qualifiedName) {
    for (PsiModifierList sublist : mySublists) {
      final PsiAnnotation annotation = sublist.findAnnotation(qualifiedName);
      if (annotation != null) return annotation;
    }

    return null;
  }

  public boolean hasModifierProperty(@NotNull final String name) {
    for (PsiModifierList sublist : mySublists) {
      if (sublist.hasModifierProperty(name)) return true;
    }
    return false;
  }

  public boolean hasExplicitModifier(@NotNull final String name) {
    for (PsiModifierList sublist : mySublists) {
      if (sublist.hasExplicitModifier(name)) return true;
    }
    return false;
  }
}