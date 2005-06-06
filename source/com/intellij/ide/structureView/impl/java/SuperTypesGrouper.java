package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import gnu.trove.THashMap;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.Collections;

public class SuperTypesGrouper implements Grouper{
  public static final Key<WeakReference<PsiMethod>> SUPER_METHOD_KEY = Key.create("StructureTreeBuilder.SUPER_METHOD_KEY");
  public static final String ID = "SHOW_INTERFACES";

  public Collection<Group> group(final AbstractTreeNode parent, Collection<TreeElement> children) {
    if (isParentGrouped(parent)) return Collections.EMPTY_LIST;
    Map<Group, SuperTypeGroup> groups = new THashMap<Group, SuperTypeGroup>();

    for (TreeElement child : children) {
      if (child instanceof PsiMethodTreeElement) {
        PsiMethod method = ((PsiMethodTreeElement)child).getMethod();
        PsiMethod[] superMethods = method.findSuperMethods();

        if (superMethods.length > 0) {
          PsiMethod superMethod = superMethods[0];
          method.putUserData(SUPER_METHOD_KEY, new WeakReference<PsiMethod>(superMethod));
          PsiClass superClass = superMethod.getContainingClass();
          boolean overrides = methodOverridesSuper(method, superMethod);
          SuperTypeGroup superTypeGroup = new SuperTypeGroup(superClass, overrides);
          SuperTypeGroup existing = groups.get(superTypeGroup);
          if (existing == null) {
            groups.put(superTypeGroup, superTypeGroup);
            existing = superTypeGroup;
          }
          existing.addMethod(child);
        }
      }
    }
    return groups.keySet();
  }

  private static boolean isParentGrouped(AbstractTreeNode parent) {
    while (parent != null) {
      if (parent.getValue() instanceof SuperTypeGroup) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static boolean methodOverridesSuper(PsiMethod method, PsiMethod superMethod) {
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
