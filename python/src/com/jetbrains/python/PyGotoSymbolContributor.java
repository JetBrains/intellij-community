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
package com.jetbrains.python;

import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyGotoSymbolContributor implements GotoClassContributor {
  @NotNull
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    Set<String> symbols = new HashSet<>();
    symbols.addAll(PyClassNameIndex.allKeys(project));
    symbols.addAll(PyModuleNameIndex.getAllKeys(project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyFunctionNameIndex.KEY, project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyVariableNameIndex.KEY, project));
    return ArrayUtil.toStringArray(symbols);
  }

  @NotNull
  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project, final boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? PyProjectScopeBuilder.excludeSdkTestsScope(project)
                                    : GlobalSearchScope.projectScope(project);

    List<NavigationItem> symbols = new ArrayList<>();
    symbols.addAll(PyClassNameIndex.find(name, project, scope));
    symbols.addAll(PyModuleNameIndex.find(name, project, includeNonProjectItems));
    symbols.addAll(PyFunctionNameIndex.find(name, project, scope));
    symbols.addAll(PyVariableNameIndex.find(name, project, scope));

    return symbols.toArray(new NavigationItem[symbols.size()]);
  }

  @Override
  public String getQualifiedName(NavigationItem item) {
    if (item instanceof PyQualifiedNameOwner) {
      return ((PyQualifiedNameOwner) item).getQualifiedName();
    }
    return null;
  }

  @Override
  public String getQualifiedNameSeparator() {
    return ".";
  }
}
