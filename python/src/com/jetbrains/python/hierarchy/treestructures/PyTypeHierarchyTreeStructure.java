package com.jetbrains.python.hierarchy.treestructures;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.hierarchy.PyTypeHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Jul 31, 2009
 * Time: 6:29:00 PM
 */
public class PyTypeHierarchyTreeStructure extends PySubTypesHierarchyTreeStructure {
  private static PyTypeHierarchyNodeDescriptor buildHierarchyElement(@NotNull final PyClass cl) {
    PyTypeHierarchyNodeDescriptor descriptor = null;
    PyClass[] superClasses = getSuperClasses(cl);
    for (int i = superClasses.length - 1; i >= 0; --i) {
      final PyClass superClass = superClasses[i];
      final PyTypeHierarchyNodeDescriptor newDescriptor = new PyTypeHierarchyNodeDescriptor(descriptor, superClass, false);
      if (descriptor != null) {
        newDescriptor.setCachedChildren(new PyTypeHierarchyNodeDescriptor[]{newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final PyTypeHierarchyNodeDescriptor newDescriptor = new PyTypeHierarchyNodeDescriptor(descriptor, cl, true);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
    }
    return newDescriptor;
  }

  private static PyClass[] getSuperClasses(@NotNull PyClass cl) {
    List<PyClass> superClasses = new ArrayList<PyClass>();
    while (true) {
      final PyClass[] classes = cl.getSuperClasses();
      if (classes.length == 0) {
        break;
      }
      superClasses.addAll(Arrays.asList(classes));
      cl = classes[0];
    }
    return superClasses.toArray(new PyClass[superClasses.size()]);
  }

  protected PyTypeHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor baseDescriptor) {
    super(project, baseDescriptor);
  }

  public PyTypeHierarchyTreeStructure(@NotNull final PyClass cl) {
    super(cl.getProject(), buildHierarchyElement(cl));
    setBaseElement(myBaseDescriptor);
  }
}
