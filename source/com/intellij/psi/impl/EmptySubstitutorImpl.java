package com.intellij.psi.impl;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 *  @author dsl
 */
public final class EmptySubstitutorImpl extends EmptySubstitutor implements PsiSubstitutor {
  public PsiType substitute(PsiTypeParameter typeParameter){
    return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter);
  }

  public PsiType substitute(PsiType type){
    return type;
  }

  public PsiType substituteWithBoundsPromotion(PsiTypeParameter typeParameter) {
    return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter);
  }

  public PsiSubstitutor put(PsiTypeParameter classParameter, PsiType mapping){
    final PsiSubstitutor substitutor = new PsiSubstitutorImpl();
    return substitutor.put(classParameter, mapping);
  }
  public PsiSubstitutor putAll(PsiClass parentClass, PsiType[] mappings){
    if(!parentClass.hasTypeParameters()) return this;
    final PsiSubstitutor substitutor = new PsiSubstitutorImpl();
    return substitutor.putAll(parentClass, mappings);
  }

  public PsiSubstitutor putAll(PsiSubstitutor another) {
    return another;
  }

  @NotNull
  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return Collections.emptyMap();
  }

  public boolean isValid() {
    return true;
  }

  public PsiType substituteNoErase(final PsiType type) {
    return type;
  }
}
