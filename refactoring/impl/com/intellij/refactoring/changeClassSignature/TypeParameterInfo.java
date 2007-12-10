package com.intellij.refactoring.changeClassSignature;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
class TypeParameterInfo {
  private final int myOldParameterIndex;
  private String myNewName;
  private CanonicalTypes.Type myDefaultValue;

  TypeParameterInfo(int oldIndex) {
    myOldParameterIndex = oldIndex;
    myDefaultValue = null;
  }

  TypeParameterInfo(String name, PsiType aType) {
    myOldParameterIndex = -1;
    myNewName = name;
    if (aType != null) {
      myDefaultValue = CanonicalTypes.createTypeWrapper(aType);
    }
    else {
      myDefaultValue = null;
    }
  }

  TypeParameterInfo(PsiClass aClass, String name, String defaultValue) throws IncorrectOperationException {
    this(name, JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createTypeFromText(defaultValue, aClass.getLBrace()));
  }


  public int getOldParameterIndex() {
    return myOldParameterIndex;
  }

  public String getNewName() {
    return myNewName;
  }

  public void setNewName(String newName) {
    myNewName = newName;
  }

  public CanonicalTypes.Type getDefaultValue() {
    return myDefaultValue;
  }

  public void setDefaultValue(CanonicalTypes.Type defaultValue) {
    myDefaultValue = defaultValue;
  }

  public void setDefaultValue(PsiType aType) {
    setDefaultValue(CanonicalTypes.createTypeWrapper(aType));
  }

  boolean isForExistingParameter() {
    return myOldParameterIndex >= 0;
  }
}
