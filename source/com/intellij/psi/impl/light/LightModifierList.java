package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class LightModifierList extends LightElement implements PsiModifierList{
  public LightModifierList(PsiManager manager){
    super(manager);
  }

  public boolean hasModifierProperty(String name){
    return false;
  }

  public void setModifierProperty(String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkSetModifierProperty(String name, boolean value) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  public PsiAnnotation findAnnotation(String qualifiedName) {
    return null;
  }

  public String getText(){
    return null;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitModifierList(this);
  }

  public PsiElement copy(){
    return null;
  }

  public String toString(){
    return "PsiModifierList";
  }

}