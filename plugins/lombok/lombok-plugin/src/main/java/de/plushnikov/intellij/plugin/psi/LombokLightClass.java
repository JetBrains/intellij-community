package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.light.LightClass;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public class LombokLightClass extends LightClass {
  private String myQualifiedName;
  private PsiMethod[] myConstructors = new PsiMethod[0];
  private PsiField[] myFields = new PsiField[0];
  private PsiMethod[] myMethods = new PsiMethod[0];
  private LombokLightModifierList myModifierList;
  private PsiClass myContainingClass;
  private PsiTypeParameterList myTypeParameterList;
  private PsiTypeParameter[] myTypeParameters = new PsiTypeParameter[0];

  public LombokLightClass(@NotNull PsiClass delegate) {
    super(delegate);
    myModifierList = new LombokLightModifierList(delegate.getManager(), JavaLanguage.INSTANCE);
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  public void setQualifiedName(@NotNull String qualifiedName) {
    myQualifiedName = qualifiedName;
  }

  @NotNull
  @Override
  public PsiMethod[] getConstructors() {
    return myConstructors;
  }

  public void setConstructors(@NotNull PsiMethod[] constructors) {
    myConstructors = constructors;
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return myFields;
  }

  public void setFields(@NotNull PsiField[] fields) {
    myFields = fields;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    // http://stackoverflow.com/a/784842/411905
    PsiMethod[] result = Arrays.copyOf(myMethods, myMethods.length + myConstructors.length);
    System.arraycopy(myConstructors, 0, result, myMethods.length, myConstructors.length);
    return result;
  }

  public void setMethods(@NotNull PsiMethod[] methods) {
    myMethods = methods;
  }

  @NotNull
  @Override
  public LombokLightModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  @Nullable
  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return myTypeParameterList;
  }

  @NotNull
  @Override
  public PsiTypeParameter[] getTypeParameters() {
    return myTypeParameters;
  }

  public void setTypeParameterList(@Nullable PsiTypeParameterList list) {
    myTypeParameterList = list;
    if (null == myTypeParameterList) {
      myTypeParameters = PsiTypeParameter.EMPTY_ARRAY;
    } else {
      myTypeParameters = myTypeParameterList.getTypeParameters();
    }
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public void setContainingClass(@Nullable PsiClass containingClass) {
    myContainingClass = containingClass;
  }

  @Override
  public PsiElement getScope() {
    if (myContainingClass != null) {
      return myContainingClass.getScope();
    }
    return super.getScope();
  }

  @Override
  public PsiElement getParent() {
    if (myContainingClass != null) {
      return myContainingClass.getParent();
    }
    return super.getParent();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }
}
