/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.beanProperties;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class BeanProperty {

  private static final Icon ICON = IconLoader.getIcon("/nodes/property.png");

  private final PsiMethod myMethod;

  protected BeanProperty(@NotNull PsiMethod method) {
    myMethod = method;
  }

  public PsiElement getPsiElement() {
    return new BeanPropertyElement();
  }

  @NotNull
  public String getName() {
    final String name = PropertyUtil.getPropertyName(myMethod);
    assert name != null;
    return name;
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

  private class BeanPropertyElement extends FakePsiElement implements PsiMetaBaseOwner, PsiPresentableMetaData {

    public PsiManager getManager() {
      return myMethod.getManager();
    }

    public PsiElement getDeclaration() {
      return this;
    }

    @NonNls
    public String getName(PsiElement context) {
      return getName();
    }

    @NotNull
    public String getName() {
      return BeanProperty.this.getName();
    }

    public void init(PsiElement element) {

    }

    public Object[] getDependences() {
      return new Object[0];
    }

    @Nullable
    public Icon getIcon(int flags) {
      return BeanProperty.this.getIcon(flags);
    }

    public PsiElement getParent() {
      return myMethod;
    }

    @Nullable
    public PsiMetaDataBase getMetaData() {
      return this;
    }

    public String getTypeName() {
      return IdeBundle.message("bean.property");
    }

    @Nullable
    public Icon getIcon() {
      return getIcon(0);
    }
  }
}
