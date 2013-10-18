package com.jetbrains.python.hierarchy.treestructures;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.jetbrains.python.hierarchy.PyTypeHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 12, 2009
 * Time: 7:03:13 PM
 */
public class PySubTypesHierarchyTreeStructure extends HierarchyTreeStructure {
  protected PySubTypesHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor baseDescriptor) {
    super(project, baseDescriptor);
  }

  public PySubTypesHierarchyTreeStructure(@NotNull final PyClass cl) {
    super(cl.getProject(), new PyTypeHierarchyNodeDescriptor(null, cl, true));
  }

  @NotNull
  protected Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
    final PyClass classElement = ((PyTypeHierarchyNodeDescriptor)descriptor).getClassElement();
    Query<PyClass> subClasses = PyClassInheritorsSearch.search(classElement, false);

    List<PyTypeHierarchyNodeDescriptor> res = new ArrayList<PyTypeHierarchyNodeDescriptor>();
    for (PyClass cl : subClasses) {
      res.add(new PyTypeHierarchyNodeDescriptor(descriptor, cl, false));
    }

    return ArrayUtil.toObjectArray(res);
  }
}
