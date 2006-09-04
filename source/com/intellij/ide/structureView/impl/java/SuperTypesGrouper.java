package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class SuperTypesGrouper implements Grouper{
  public static final Key<WeakReference<PsiMethod>> SUPER_METHOD_KEY = Key.create("StructureTreeBuilder.SUPER_METHOD_KEY");
  @NonNls public static final String ID = "SHOW_INTERFACES";

  @NotNull
  public Collection<Group> group(final AbstractTreeNode parent, Collection<TreeElement> children) {
    if (isParentGrouped(parent)) return Collections.emptyList();
    Map<Group, SuperTypeGroup> groups = new THashMap<Group, SuperTypeGroup>();

    for (TreeElement child : children) {
      if (child instanceof PsiMethodTreeElement) {
        final PsiMethodTreeElement element = (PsiMethodTreeElement)child;

        PsiMethod method = ((PsiMethodTreeElement)child).getMethod();
        if (element.isInherited()) {
          PsiClass groupClass = method.getContainingClass();
          final SuperTypeGroup group = getOrCreateGroup(groupClass, SuperTypeGroup.OwnershipType.INHERITS, groups);
          group.addMethod(child);
        }
        else {
          PsiMethod[] superMethods = method.findSuperMethods();

          if (superMethods.length > 0) {
            PsiMethod superMethod = superMethods[0];
            method.putUserData(SUPER_METHOD_KEY, new WeakReference<PsiMethod>(superMethod));
            PsiClass groupClass = superMethod.getContainingClass();
            boolean overrides = methodOverridesSuper(method, superMethod);
            final SuperTypeGroup.OwnershipType ownershipType =
              overrides ? SuperTypeGroup.OwnershipType.OVERRIDES : SuperTypeGroup.OwnershipType.IMPLEMENTS;
            SuperTypeGroup group = getOrCreateGroup(groupClass, ownershipType, groups);
            group.addMethod(child);
          }
        }
      }
    }
    return groups.keySet();
  }

  private static SuperTypeGroup getOrCreateGroup(final PsiClass groupClass, final SuperTypeGroup.OwnershipType ownershipType, final Map<Group, SuperTypeGroup> groups) {
    SuperTypeGroup superTypeGroup =
      new SuperTypeGroup(groupClass, ownershipType);
    SuperTypeGroup existing = groups.get(superTypeGroup);
    if (existing == null) {
      groups.put(superTypeGroup, superTypeGroup);
      existing = superTypeGroup;
    }
    return existing;
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

  @NotNull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.group.methods.by.defining.type"), null,
                                      IconLoader.getIcon("/general/implementingMethod.png"));
  }

  @NotNull
  public String getName() {
    return ID;
  }

}
