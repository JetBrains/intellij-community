/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 28, 2001
 * Time: 4:17:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;

public class RefImplicitConstructor extends RefMethod {
  private RefClass myOwnerClass;

  public RefImplicitConstructor(RefClass ownerClass) {
    super("implicit constructor of " + ownerClass.getName(), ownerClass);
    myOwnerClass = ownerClass;
  }

  public void buildReferences() {
  }

  public boolean isSuspicious() {
    return getOwnerClass().isSuspicious();
  }

  public String getName() {
    return "implicit constructor of " + getOwnerClass().getName();
  }

  public String getExternalName() {
    return getOwnerClass().getExternalName();
  }

  public boolean isValid() {
    return getOwnerClass().isValid();
  }

  public String getAccessModifier() {
    return getOwnerClass().getAccessModifier();
  }

  public void setAccessModifier(String am) {
    getOwnerClass().setAccessModifier(am);
  }

  public PsiModifierListOwner getElement() {
    return getOwnerClass().getElement();
  }

  public RefClass getOwnerClass() {
    return myOwnerClass == null ? super.getOwnerClass() : myOwnerClass;
  }
}
