/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  @NotNull
  protected Object[] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final PyClass[] superClasses = ((PyTypeHierarchyNodeDescriptor)descriptor).getClassElement().getSuperClasses();
    List<PyTypeHierarchyNodeDescriptor> res = new ArrayList<PyTypeHierarchyNodeDescriptor>();
    for (PyClass superClass : superClasses) {
      res.add(new PyTypeHierarchyNodeDescriptor(descriptor, superClass, false));
    }
    return res.toArray();
  }
}
