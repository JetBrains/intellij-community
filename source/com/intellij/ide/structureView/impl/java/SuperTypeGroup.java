package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

import javax.swing.*;
import java.lang.ref.WeakReference;

public class SuperTypeGroup implements Group, ItemPresentation, AccessLevelProvider{
  private final SmartPsiElementPointer mySuperClassPointer;
  private final boolean myOverrides;
  private static final Icon OVERRIDING_ICON = IconLoader.getIcon("/general/overridingMethod.png");
  private static final Icon IMLLEMENTING_ICON = IconLoader.getIcon("/general/implementingMethod.png");

  public SuperTypeGroup(PsiClass superClass, boolean overrides) {
    myOverrides = overrides;
    mySuperClassPointer = SmartPointerManager.getInstance(superClass.getProject()).createSmartPsiElementPointer(superClass);
  }

  public boolean contains(TreeElement o) {
    if (o instanceof PsiMethodTreeElement) {
      PsiMethod method = ((PsiMethodTreeElement)o).getMethod();
      WeakReference<PsiMethod> ref = method.getUserData(SuperTypesGrouper.SUPER_METHOD_KEY);
      if (ref == null) return false;
      PsiMethod superMethod = ref.get();
      if (superMethod == null) return false;
      PsiClass superClass = superMethod.getContainingClass();
      if (!superClass.equals(getSuperClass())) return false;
      boolean overrides = SuperTypesGrouper.methodOverridesSuper(method, superMethod);
      if (overrides != myOverrides) return false;
      method.putUserData(SuperTypesGrouper.SUPER_METHOD_KEY, null);
      return true;
    }
    else {
      return false;
    }

  }

  private PsiClass getSuperClass() {
    return (PsiClass)mySuperClassPointer.getElement();
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public Icon getIcon(boolean open) {
    if (myOverrides) {
      return OVERRIDING_ICON;
    } else {
      return IMLLEMENTING_ICON;
    }
  }

  public String getLocationString() {
    return null;
  }

  public String getPresentableText() {
    return toString();
  }

  public String toString() {
    return getSuperClass().getName();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SuperTypeGroup)) return false;

    final SuperTypeGroup superTypeGroup = (SuperTypeGroup)o;

    if (myOverrides != superTypeGroup.myOverrides) return false;
    if (getSuperClass() != null ? !getSuperClass() .equals(superTypeGroup.getSuperClass() ) : superTypeGroup.getSuperClass()  != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (getSuperClass()  != null ? getSuperClass() .hashCode() : 0);
    result = 29 * result + (myOverrides ? 1 : 0);
    return result;
  }

  public Object getValue() {
    return this;
  }

  public int getAccessLevel() {
    return PsiUtil.getAccessLevel(getSuperClass() .getModifierList());
  }

  public int getSubLevel() {
    return 1;
  }

  public TextAttributesKey getTextAttributesKey() {
    return null;
  }
}
