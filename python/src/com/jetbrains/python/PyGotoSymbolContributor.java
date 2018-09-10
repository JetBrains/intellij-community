// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyGotoSymbolContributor implements GotoClassContributor {
  @Override
  @NotNull
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    Set<String> symbols = new HashSet<>();
    symbols.addAll(PyClassNameIndex.allKeys(project));
    symbols.addAll(PyModuleNameIndex.getAllKeys(project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyFunctionNameIndex.KEY, project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyVariableNameIndex.KEY, project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyClassAttributesIndex.KEY, project));
    return ArrayUtil.toStringArray(symbols);
  }

  @Override
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
    symbols.addAll(PyClassAttributesIndex.findClassAndInstanceAttributes(name, project, scope));
    return symbols.toArray(NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY);
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
