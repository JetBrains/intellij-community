package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;

import java.util.ArrayList;
import java.util.List;

public final class SupertypesHierarchyTreeStructure extends HierarchyTreeStructure {

  public SupertypesHierarchyTreeStructure(final Project project, final PsiClass aClass) {
    super(project, new TypeHierarchyNodeDescriptor(project, null, aClass, true));
  }

  protected final Object[] buildChildren(final HierarchyNodeDescriptor descriptor) {
    final PsiClass psiClass = ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
    final PsiClass[] supers = psiClass.getSupers();
    final List<HierarchyNodeDescriptor> descriptors = new ArrayList<HierarchyNodeDescriptor>();
    PsiClass objectClass = JavaPsiFacade.getInstance(myProject).findClass("java.lang.Object", psiClass.getResolveScope());
    for (PsiClass aSuper : supers) {
      if (!psiClass.isInterface() || !aSuper.equals(objectClass)) {
        descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, aSuper, false));
      }
    }
    return descriptors.toArray(new HierarchyNodeDescriptor[descriptors.size()]);
  }
}
