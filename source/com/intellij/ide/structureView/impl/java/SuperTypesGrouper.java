package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiSuperMethodUtil;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public class SuperTypesGrouper implements Grouper{
  public static final Key<WeakReference> SUPER_METHOD_KEY = Key.create("StructureTreeBuilder.SUPER_METHOD_KEY");
  public static final String ID = "SHOW_INTERFACES";

  public Collection<Group> group(Collection<TreeElement> children) {
    Collection<Group> groups = new HashSet<Group>();
    for (Iterator<TreeElement> iterator = children.iterator(); iterator.hasNext();) {
      Object child = iterator.next();
      if (child instanceof PsiMethodTreeElement) {
        PsiMethod method = ((PsiMethodTreeElement)child).getMethod();
        PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);

        if (superMethods.length > 0) {
          PsiMethod superMethod = superMethods[0];
          method.putUserData(SUPER_METHOD_KEY, new WeakReference(superMethod));
          PsiClass superClass = superMethod.getContainingClass();
          boolean overrides = methodOverridesSuper(method, superMethod);
          groups.add(new SuperTypeGroup(superClass, overrides));
        }
      }
    }
    return groups;
  }

  static boolean methodOverridesSuper(PsiMethod method, PsiMethod superMethod) {
    boolean overrides = false;
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)){
      overrides = true;
    }
    else if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)){
      overrides = true;
    }
    return overrides;

  }

  public ActionPresentation getPresentation() {
    return new ActionPresentationData("Group Methods by Defining Type", null, IconLoader.getIcon("/general/implementingMethod.png"));
  }

  public String getName() {
    return ID;
  }

}
