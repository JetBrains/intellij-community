package com.jetbrains.python.hierarchy.treestructures;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.hierarchy.PyTypeHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyTypeHierarchyTreeStructure extends PySubTypesHierarchyTreeStructure {
  private static PyTypeHierarchyNodeDescriptor buildHierarchyElement(@NotNull final PyClass cl) {
    PyTypeHierarchyNodeDescriptor descriptor = null;
    List<PyClass> superClasses = PyUtil.getAllSuperClasses(cl);
    for (int i = superClasses.size() - 1; i >= 0; --i) {
      final PyClass superClass = superClasses.get(i);
      final PyTypeHierarchyNodeDescriptor newDescriptor = new PyTypeHierarchyNodeDescriptor(descriptor, superClass, false);
      if (descriptor != null) {
        descriptor.setCachedChildren(new PyTypeHierarchyNodeDescriptor[]{newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final PyTypeHierarchyNodeDescriptor newDescriptor = new PyTypeHierarchyNodeDescriptor(descriptor, cl, true);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
    }
    return newDescriptor;
  }

  protected PyTypeHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor baseDescriptor) {
    super(project, baseDescriptor);
  }

  public PyTypeHierarchyTreeStructure(@NotNull final PyClass cl) {
    super(cl.getProject(), buildHierarchyElement(cl));
    setBaseElement(myBaseDescriptor);
  }
}
