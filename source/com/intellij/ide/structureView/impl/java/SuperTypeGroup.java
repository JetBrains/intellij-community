package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;

import javax.swing.*;
import java.lang.ref.WeakReference;

public class SuperTypeGroup implements Group, ItemPresentation, AccessLevelProvider{
  private final PsiClass mySuperClass;
  private final boolean myOverrides;

  public SuperTypeGroup(PsiClass superClass, boolean overrides) {
    myOverrides = overrides;
    mySuperClass = superClass;
  }

  public boolean contains(TreeElement o) {
    if (o instanceof PsiMethodTreeElement) {
      PsiMethod method = ((PsiMethodTreeElement)o).getMethod();
      WeakReference<PsiMethod> ref = method.getUserData(SuperTypesGrouper.SUPER_METHOD_KEY);
      if (ref == null) return false;
      PsiMethod superMethod = ref.get();
      if (superMethod == null) return false;
      PsiClass superClass = superMethod.getContainingClass();
      if (!superClass.equals(mySuperClass)) return false;
      boolean overrides = SuperTypesGrouper.methodOverridesSuper(method, superMethod);
      if (overrides != myOverrides) return false;
      method.putUserData(SuperTypesGrouper.SUPER_METHOD_KEY, null);
      return true;
    }
    else {
      return false;
    }

  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public Icon getIcon(boolean open) {
    return mySuperClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
  }

  public String getLocationString() {
    return null;
  }

  public String getPresentableText() {
    return toString();
  }

  public String toString() {
    return mySuperClass.getName();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SuperTypeGroup)) return false;

    final SuperTypeGroup superTypeGroup = (SuperTypeGroup)o;

    if (myOverrides != superTypeGroup.myOverrides) return false;
    if (mySuperClass != null ? !mySuperClass.equals(superTypeGroup.mySuperClass) : superTypeGroup.mySuperClass != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (mySuperClass != null ? mySuperClass.hashCode() : 0);
    result = 29 * result + (myOverrides ? 1 : 0);
    return result;
  }

  public Object getValue() {
    return this;
  }

  public int getAccessLevel() {
    return PsiUtil.getAccessLevel(mySuperClass.getModifierList());
  }

  public int getSubLevel() {
    return 1;
  }
}
