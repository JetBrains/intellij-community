package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Icons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;

public class PropertyGroup implements Group, ItemPresentation, AccessLevelProvider {
  private final String myPropertyName;
  private final PsiType myPropertyType;

  private PsiField myField;
  private PsiMethod myGetter;
  private PsiMethod mySetter;
  public static final Icon PROPERTY_READ_ICON = loadIcon("/nodes/propertyRead.png");
  public static final Icon PROPERTY_READ_STATIC_ICON = loadIcon("/nodes/propertyReadStatic.png");
  public static final Icon PROPERTY_WRITE_ICON = loadIcon("/nodes/propertyWrite.png");
  public static final Icon PROPERTY_WRITE_STATIC_ICON = loadIcon("/nodes/propertyWriteStatic.png");
  public static final Icon PROPERTY_READ_WRITE_ICON = loadIcon("/nodes/propertyReadWrite.png");
  public static final Icon PROPERTY_READ_WRITE_STATIC_ICON = loadIcon("/nodes/propertyReadWriteStatic.png");

  private PropertyGroup(String propertyName, PsiType propertyType) {
    myPropertyName = propertyName;
    myPropertyType = propertyType;
  }

  public static final PropertyGroup createOn(PsiElement object) {
    if (object instanceof PsiField) {
      PsiField field = ((PsiField)object);
      PropertyGroup group = new PropertyGroup(PropertyUtil.suggestPropertyName(field.getProject(), field), field.getType());
      group.setField(field);
      return group;
    }
    else if (object instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)object;
      if (PropertyUtil.isSimplePropertyGetter(method)) {
        PropertyGroup group = new PropertyGroup(PropertyUtil.getPropertyNameByGetter(method), method.getReturnType());
        group.setGetter(method);
        return group;
      }
      else if (PropertyUtil.isSimplePropertySetter(method)) {
        PropertyGroup group = new PropertyGroup(PropertyUtil.getPropertyNameBySetter(method), method.getParameterList().getParameters()[0].getType());
        group.setSetter(method);
        return group;
      }
    }
    return null;
  }

  public boolean contains(TreeElement object) {
    if (object instanceof JavaClassTreeElementBase){
      PropertyGroup objectGroup = createOn(((JavaClassTreeElementBase)object).getElement());
      return equals(objectGroup);
    }
    return false;
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public Icon getIcon(boolean open) {
    return Icons.PROPERTY_ICON;
  }

  public String getLocationString() {
    return null;
  }

  public String getPresentableText() {
    return myPropertyName + ":" + myPropertyType.getPresentableText();
  }

  public String toString() {
    return myPropertyName;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PropertyGroup)) return false;

    final PropertyGroup propertyGroup = (PropertyGroup)o;

    if (myPropertyName != null ? !myPropertyName.equals(propertyGroup.myPropertyName) : propertyGroup.myPropertyName != null) return false;
    if (myPropertyType != null ? !myPropertyType.equals(propertyGroup.myPropertyType) : propertyGroup.myPropertyType != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myPropertyName != null ? myPropertyName.hashCode() : 0);
    result = 29 * result + (myPropertyType != null ? myPropertyType.hashCode() : 0);
    return result;
  }

  public Object getValue() {
    return this;
  }

  public String getGetterName() {
    return PropertyUtil.suggestGetterName(myPropertyName, myPropertyType);
  }

  public int getAccessLevel() {
    int result = PsiUtil.ACCESS_LEVEL_PRIVATE;
    if (myGetter != null)
      result = Math.max(result, PsiUtil.getAccessLevel(myGetter.getModifierList()));
    if (mySetter != null)
      result = Math.max(result, PsiUtil.getAccessLevel(mySetter.getModifierList()));
    if (myField != null)
      result = Math.max(result, PsiUtil.getAccessLevel(myField.getModifierList()));
    return result;
  }

  public int getSubLevel() {
    return 0;
  }

  public PsiClass getParentClass() {
    return null;
  }

  public void setField(PsiField field) {
    myField = field;
  }

  public void setGetter(PsiMethod getter) {
    myGetter = getter;
  }

  public void setSetter(PsiMethod setter) {
    mySetter = setter;
  }

  public PsiField getField() {
    return myField;
  }

  public PsiMethod getGetter() {
    return myGetter;
  }

  public PsiMethod getSetter() {
    return mySetter;
  }

  public void copyAccessorsFrom(PropertyGroup group) {
    if (group.getGetter() != null) setGetter(group.getGetter());
    if (group.getSetter() != null) setSetter(group.getSetter());
    if (group.getField() != null) setField(group.getField());
  }

  private static Icon loadIcon(String resourceName) {
    Icon icon = IconLoader.getIcon(resourceName);
    Application application = ApplicationManager.getApplication();
    if (icon == null && (application != null && application.isUnitTestMode())) {
      return new ImageIcon();
    }
    return icon;
  }
}
