package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.JavaPsiFacade;

public final class SupertypesHierarchyTreeStructure extends HierarchyTreeStructure {
  public static final String TYPE = IdeBundle.message("title.hierarchy.supertypes");

  public SupertypesHierarchyTreeStructure(final Project project, final PsiClass aClass) {
    super(project, new TypeHierarchyNodeDescriptor(project, null, aClass, true));
  }

  protected final Object[] buildChildren(final HierarchyNodeDescriptor descriptor) {
    final PsiClass psiClass = ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
    final PsiClass[] supers = psiClass.getSupers();
    final int supersLength = psiClass.isInterface() ? supers.length - 1 : supers.length;
    final HierarchyNodeDescriptor[] descriptors = new HierarchyNodeDescriptor[supersLength];
    PsiClass objectClass = JavaPsiFacade.getInstance(myProject).findClass("java.lang.Object", psiClass.getResolveScope());
    for (int i = 0, j = 0; i < supers.length; i++) {
      if (!psiClass.isInterface() || !supers[i].equals(objectClass)) {
        descriptors[j++] = new TypeHierarchyNodeDescriptor(myProject, descriptor, supers[i], false);
      }
    }
    return descriptors;
  }
}
