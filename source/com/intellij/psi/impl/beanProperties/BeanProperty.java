/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.beanProperties;

import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class BeanProperty {

  static final Icon ICON = IconLoader.getIcon("/nodes/property.png");

  private final PsiMethod myMethod;

  protected BeanProperty(@NotNull PsiMethod method) {
    myMethod = method;
  }

  public PsiNamedElement getPsiElement() {
    return new BeanPropertyElement(myMethod, getName());
  }

  @NotNull
  public String getName() {
    final String name = PropertyUtil.getPropertyName(myMethod);
    return name == null ? "" : name;
  }

  @NotNull
  public PsiType getPropertyType() {
    PsiType type = PropertyUtil.getPropertyType(myMethod);
    assert type != null;
    return type;
  }

  @NotNull
  public PsiMethod getMethod() {
    return myMethod;
  }

  @Nullable
  public PsiMethod getGetter() {
    if (PropertyUtil.isSimplePropertyGetter(myMethod)) {
      return myMethod;
    }
    return PropertyUtil.findPropertyGetter(myMethod.getContainingClass(), getName(), false, true);
  }

  @Nullable
  public PsiMethod getSetter() {
    if (PropertyUtil.isSimplePropertySetter(myMethod)) {
      return myMethod;
    }
    return PropertyUtil.findPropertySetter(myMethod.getContainingClass(), getName(), false, true);
  }

  public void setName(String newName) throws IncorrectOperationException {
    final PsiMethod setter = getSetter();
    final PsiMethod getter = getGetter();
    if (getter != null) {
      final String getterName = PropertyUtil.suggestGetterName(newName, getter.getReturnType());
      getter.setName(getterName);
    }
    if (setter != null) {
      final String setterName = PropertyUtil.suggestSetterName(newName);
      setter.setName(setterName);
    }
  }

  @Nullable
  public Icon getIcon(int flags) {
    return ICON;
  }

  @Nullable
  public static BeanProperty createBeanProperty(@NotNull PsiMethod method) {
    return PropertyUtil.isSimplePropertyAccessor(method) ? new BeanProperty(method) : null;
  }

}
