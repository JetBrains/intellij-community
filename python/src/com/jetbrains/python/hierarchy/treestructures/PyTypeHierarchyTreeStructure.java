/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.hierarchy.treestructures;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyTypeHierarchyTreeStructure extends PySubTypesHierarchyTreeStructure {
  public PyTypeHierarchyTreeStructure(@NotNull final PyClass cl) {
    super(cl.getProject(), buildHierarchyElement(cl));
    setBaseElement(myBaseDescriptor);
  }

  private static PyHierarchyNodeDescriptor buildHierarchyElement(@NotNull final PyClass cl) {
    PyHierarchyNodeDescriptor descriptor = null;
    List<PyClass> superClasses = PyUtil.getAllSuperClasses(cl);
    for (int i = superClasses.size() - 1; i >= 0; --i) {
      final PyClass superClass = superClasses.get(i);
      final PyHierarchyNodeDescriptor newDescriptor = new PyHierarchyNodeDescriptor(descriptor, superClass, false);
      if (descriptor != null) {
        descriptor.setCachedChildren(new PyHierarchyNodeDescriptor[]{newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final PyHierarchyNodeDescriptor newDescriptor = new PyHierarchyNodeDescriptor(descriptor, cl, true);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
    }
    return newDescriptor;
  }
}
