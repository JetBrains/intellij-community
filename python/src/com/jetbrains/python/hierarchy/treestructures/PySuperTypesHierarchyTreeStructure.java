package com.jetbrains.python.hierarchy.treestructures;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.hierarchy.PyTypeHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 12, 2009
 * Time: 7:04:07 PM
 */
public class PySuperTypesHierarchyTreeStructure extends HierarchyTreeStructure {
  protected PySuperTypesHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor baseDescriptor) {
    super(project, baseDescriptor);
  }

  public PySuperTypesHierarchyTreeStructure(@NotNull final PyClass cl) {
    super(cl.getProject(), new PyTypeHierarchyNodeDescriptor(null, cl, true));
  }

  protected Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
    final PyClass[] superClasses = ((PyTypeHierarchyNodeDescriptor)descriptor).getClassElement().getSuperClasses();
    List<PyTypeHierarchyNodeDescriptor> res = new ArrayList<PyTypeHierarchyNodeDescriptor>();
    for (PyClass superClass : superClasses) {
      res.add(new PyTypeHierarchyNodeDescriptor(descriptor, superClass, false));
    }
    return res.toArray();
  }
}
