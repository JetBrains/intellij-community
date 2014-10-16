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
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.intellij.ide.util.AbstractTreeClassChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class PyClassTreeChooserDialog extends AbstractTreeClassChooserDialog<PyClass>{
  public PyClassTreeChooserDialog(String title, Project project, GlobalSearchScope scope, @Nullable Filter<PyClass> classFilter,
                                  @Nullable PyClass initialClass) {
    super(title, project, scope, PyClass.class, classFilter, initialClass);
  }

  @NotNull
  @Override
  protected List<PyClass> getClassesByName(String name,
                                           boolean checkBoxState,
                                           String pattern,
                                           GlobalSearchScope searchScope) {
    final Collection<PyClass> classes = PyClassNameIndex.find(name, getProject(), searchScope.isSearchInLibraries());
    final List<PyClass> result = Lists.newArrayList();
    for (PyClass c: classes) {
      if (getFilter().isAccepted(c)) {
        result.add(c);
      }
    }

    return result;
  }

  @Override
  @Nullable
  protected PyClass getSelectedFromTreeUserObject(DefaultMutableTreeNode node) {
    return null;
  }
}
