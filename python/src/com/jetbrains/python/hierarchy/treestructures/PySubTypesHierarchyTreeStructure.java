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
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
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
    super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
  }

  @NotNull
  protected Object[] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final List<PyHierarchyNodeDescriptor> res = new ArrayList<>();
    final PsiElement element = ((PyHierarchyNodeDescriptor)descriptor).getPsiElement();
    if (element instanceof PyClass) {
      final PyClass cls = (PyClass)element;
      Query<PyClass> subClasses = PyClassInheritorsSearch.search(cls, false);
      for (PyClass subClass : subClasses) {
        res.add(new PyHierarchyNodeDescriptor(descriptor, subClass, false));
      }

    }

    return ArrayUtil.toObjectArray(res);
  }
}
