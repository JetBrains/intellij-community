package com.intellij.ide.hierarchy.type;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class SubtypesHierarchyTreeStructure extends HierarchyTreeStructure {
  public static final String TYPE = IdeBundle.message("title.hierarchy.subtypes");

  protected SubtypesHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor descriptor) {
    super(project, descriptor);
  }

  public SubtypesHierarchyTreeStructure(final Project project, final PsiClass psiClass) {
    super(project, new TypeHierarchyNodeDescriptor(project, null, psiClass, true));
  }

  protected final Object[] buildChildren(final HierarchyNodeDescriptor descriptor) {
    final PsiClass psiClass = ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
    if ("java.lang.Object".equals(psiClass.getQualifiedName())) {
      return new Object[]{IdeBundle.message("node.hierarchy.java.lang.object")};
    }
    if (psiClass instanceof PsiAnonymousClass) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    if (psiClass.hasModifierProperty(PsiModifier.FINAL)) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final List<PsiClass> classes = new ArrayList<PsiClass>(ClassInheritorsSearch.search(psiClass, psiClass.getUseScope(), false).findAll());
    final HierarchyNodeDescriptor[] descriptors = new HierarchyNodeDescriptor[classes.size()];
    for (int i = 0; i < classes.size(); i++) {
      descriptors[i] = new TypeHierarchyNodeDescriptor(myProject, descriptor, classes.get(i), false);
    }
    return descriptors;
  }
}
