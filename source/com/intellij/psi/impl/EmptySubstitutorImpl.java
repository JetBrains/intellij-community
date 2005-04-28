package com.intellij.psi.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.*;

import java.util.Collections;
import java.util.Map;

/**
 *  @author dsl
 */
public final class EmptySubstitutorImpl extends EmptySubstitutor implements PsiSubstitutorEx, ApplicationComponent {
  public PsiType substitute(PsiTypeParameter typeParameter){
    return typeParameter.getManager().getElementFactory().createType(typeParameter);
  }

  public PsiType substitute(PsiType type){
    return type;
  }

  public PsiType substituteAndCapture(PsiType type) {
    return type;
  }

  public PsiType substituteAndFullCapture(PsiType type) {
    return type;
  }

  public PsiType substituteInternal(PsiType type) {
    return substitute(type);
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

  public PsiSubstitutor merge(PsiSubstitutor other){
    return other;
  }

  public String toString() {
    return "EMPTY";
  }

  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return Collections.EMPTY_MAP;
  }

  public boolean isValid() {
    return true;
  }

  public String getComponentName() {
    return "EmptySubstitutorComponent";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public PsiSubstitutor inplacePut(PsiTypeParameter classParameter, PsiType mapping) {
    return put(classParameter, mapping);
  }

  public PsiSubstitutor inplacePutAll(PsiClass parentClass, PsiType[] mappings) {
    return putAll(parentClass, mappings);
  }
}
